/*
 * Copyright (c) 2025 Meshtastic LLC
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.geeksville.mesh.repository.radio

import android.app.Application
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattService
import com.geeksville.mesh.CoroutineDispatchers
import com.geeksville.mesh.android.Logging
import com.geeksville.mesh.concurrent.handledLaunch
import com.geeksville.mesh.repository.bluetooth.BluetoothRepository
import com.geeksville.mesh.service.*
import com.geeksville.mesh.util.anonymize
import com.geeksville.mesh.util.exceptionReporter
import com.geeksville.mesh.util.ignoreException
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import java.lang.reflect.Method
import java.util.*


/* Info for the esp32 device side code.  See that source for the 'gold' standard docs on this interface.

MeshBluetoothService UUID 6ba1b218-15a8-461f-9fa8-5dcae273eafd

FIXME - notify vs indication for fromradio output.  Using notify for now, not sure if that is best
FIXME - in the esp32 mesh management code, occasionally mirror the current net db to flash, so that if we reboot we still have a good guess of users who are out there.
FIXME - make sure this protocol is guaranteed robust and won't drop packets

"According to the BLE specification the notification length can be max ATT_MTU - 3. The 3 bytes subtracted is the 3-byte header(OP-code (operation, 1 byte) and the attribute handle (2 bytes)).
In BLE 4.1 the ATT_MTU is 23 bytes (20 bytes for payload), but in BLE 4.2 the ATT_MTU can be negotiated up to 247 bytes."

MAXPACKET is 256? look into what the lora lib uses. FIXME

Characteristics:
UUID
properties
description

8ba2bcc2-ee02-4a55-a531-c525c5e454d5
read
fromradio - contains a newly received packet destined towards the phone (up to MAXPACKET bytes? per packet).
After reading the esp32 will put the next packet in this mailbox.  If the FIFO is empty it will put an empty packet in this
mailbox.

f75c76d2-129e-4dad-a1dd-7866124401e7
write
toradio - write ToRadio protobufs to this charstic to send them (up to MAXPACKET len)

ed9da18c-a800-4f66-a670-aa7547e34453
read|notify|write
fromnum - the current packet # in the message waiting inside fromradio, if the phone sees this notify it should read messages
until it catches up with this number.
  The phone can write to this register to go backwards up to FIXME packets, to handle the rare case of a fromradio packet was dropped after the esp32
callback was called, but before it arrives at the phone.  If the phone writes to this register the esp32 will discard older packets and put the next packet >= fromnum in fromradio.
When the esp32 advances fromnum, it will delay doing the notify by 100ms, in the hopes that the notify will never actally need to be sent if the phone is already pulling from fromradio.
  Note: that if the phone ever sees this number decrease, it means the esp32 has rebooted.

Re: queue management
Not all messages are kept in the fromradio queue (filtered based on SubPacket):
* only the most recent Position and User messages for a particular node are kept
* all Data SubPackets are kept
* No WantNodeNum / DenyNodeNum messages are kept
A variable keepAllPackets, if set to true will suppress this behavior and instead keep everything for forwarding to the phone (for debugging)

 */




/**
 * Handles the bluetooth link with a mesh radio device.  Does not cache any device state,
 * just does bluetooth comms etc...
 *
 * This service is not exposed outside of this process.
 *
 * Note - this class intentionally dumb.  It doesn't understand protobuf framing etc...
 * It is designed to be simple so it can be stubbed out with a simulated version as needed.
 */
class BluetoothInterface @AssistedInject constructor(
    context: Application,
    bluetoothRepository: BluetoothRepository,
    private val dispatchers: CoroutineDispatchers,
    private val service: RadioInterfaceService,
    @Assisted val address: String,
) : IRadioInterface, Logging {

    companion object {
        /// this service UUID is publicly visible for scanning
        val BTM_SERVICE_UUID: UUID = UUID.fromString("6ba1b218-15a8-461f-9fa8-5dcae273eafd")

        val BTM_FROMRADIO_CHARACTER: UUID =
            UUID.fromString("2c55e69e-4993-11ed-b878-0242ac120002")
        val BTM_TORADIO_CHARACTER: UUID =
            UUID.fromString("f75c76d2-129e-4dad-a1dd-7866124401e7")
        val BTM_FROMNUM_CHARACTER: UUID =
            UUID.fromString("ed9da18c-a800-4f66-a670-aa7547e34453")

        /**
         * this is created in onCreate()
         * We do an ugly hack of keeping it in the singleton so we can share it for the rare software update case
         */
        @Volatile
        var safe: SafeBluetooth? = null

        private const val RECONNECT_DELAY_MILLIS = 1500L
        private const val CONNECT_ATTEMPT_TIMEOUT_MILLIS = 30_000L
    }


    /// Our BLE device
    val device
        get() = (safe ?: throw RadioNotConnectedException("No SafeBluetooth")).gatt
            ?: throw RadioNotConnectedException("No GATT")

    /// Our service - note - it is possible to get back a null response for getService if the device services haven't yet been found
    private val bservice
        get(): BluetoothGattService = device.getService(BTM_SERVICE_UUID)
            ?: throw RadioNotConnectedException("BLE service not found")

    private lateinit var fromNum: BluetoothGattCharacteristic

    /**
     * With the new rev2 api, our first send is to start the configure readbacks.  In that case,
     * rather than waiting for FromNum notifies - we try to just aggressively read all of the responses.
     */
    private var isFirstSend = true

    // NRF52 targets do not need the nasty force refresh hack that ESP32 needs (because they keep their
    // BLE handles stable.  So turn the hack off for these devices.  FIXME - find a better way to know that the board is NRF52 based
    // and Amazon fire devices seem to not need this hack either
    // Build.MANUFACTURER != "Amazon" &&
    private val needForceRefresh = !address.startsWith("FD:10:04")

    init {
        // Note: this call does no comms, it just creates the device object (even if the
        // device is off/not connected)
        val device = bluetoothRepository.getRemoteDevice(address)
        if (device != null) {
            info("Creating radio interface service.  device=${address.anonymize}")

            // Note this constructor also does no comm
            val s = SafeBluetooth(context, device, dispatchers.io)
            safe = s

            startConnect()
        } else {
            errormsg("Bluetooth adapter not found, assuming running on the emulator!")
        }
    }


    /// Send a packet/command out the radio link
    override fun handleSendToRadio(p: ByteArray) {
        try {
            safe?.let { s ->
                val uuid = BTM_TORADIO_CHARACTER
                debug("queuing ${p.size} bytes to $uuid")

                // Note: we generate a new characteristic each time, because we are about to
                // change the data and we want the data stored in the closure
                val toRadio = getCharacteristic(uuid)

                s.asyncWriteCharacteristic(toRadio, p) { r ->
                    try {
                        r.getOrThrow()
                        debug("write of ${p.size} bytes to $uuid completed")

                        if (isFirstSend) {
                            isFirstSend = false
                            doReadFromRadio(false)
                        }
                    } catch (ex: Exception) {
                        scheduleReconnect("error during asyncWriteCharacteristic - disconnecting, ${ex.message}")
                    }
                }
            }
        } catch (ex: BLEException) {
            scheduleReconnect("error during handleSendToRadio ${ex.message}")
        }
    }

    @Volatile
    private var reconnectJob: Job? = null

    /**
     * We had some problem, schedule a reconnection attempt (if one isn't already queued)
     */
    private fun scheduleReconnect(reason: String) {
        if (reconnectJob == null) {
            warn("Scheduling reconnect because $reason")
            reconnectJob = service.serviceScope.handledLaunch { retryDueToException() }
        } else {
            warn("Skipping reconnect for $reason")
        }
    }

    /// Attempt to read from the fromRadio mailbox, if data is found broadcast it to android apps
    private fun doReadFromRadio(firstRead: Boolean) {
        safe?.let { s ->
            val fromRadio = getCharacteristic(BTM_FROMRADIO_CHARACTER)
            s.asyncReadCharacteristic(fromRadio) {
                try {
                    val b = it.getOrThrow()
                        .value.clone() // We clone the array just in case, I'm not sure if they keep reusing the array

                    if (b.isNotEmpty()) {
                        debug("Received ${b.size} bytes from radio")
                        service.handleFromRadio(b)

                        // Queue up another read, until we run out of packets
                        doReadFromRadio(firstRead)
                    } else {
                        debug("Done reading from radio, fromradio is empty")
                        if (firstRead) // If we just finished our initial download, now we want to start listening for notifies
                            startWatchingFromNum()
                    }
                } catch (ex: BLEException) {
                    scheduleReconnect("error during doReadFromRadio - disconnecting, ${ex.message}")
                }
            }
        }
    }

    /**
     * Android caches old services.  But our service is still changing often, so force it to reread the service definitions every
     * time
     */
    private fun forceServiceRefresh() {
        exceptionReporter {
            // If the gatt has been destroyed, skip the refresh attempt
            safe?.gatt?.let { gatt ->
                debug("DOING FORCE REFRESH")
                val refresh: Method = gatt.javaClass.getMethod("refresh")
                refresh.invoke(gatt)
            }
        }
    }

    @Volatile
    var fromNumChanged = false

    private fun startWatchingFromNum() {
        safe?.setNotify(fromNum, true) {
            // We might get multiple notifies before we get around to reading from the radio - so just set one flag
            fromNumChanged = true
            service.serviceScope.handledLaunch {
                try {
                    if (fromNumChanged) {
                        fromNumChanged = false
                        debug("fromNum changed, so we are reading new messages")
                        doReadFromRadio(false)
                    }
                } catch (e: RadioNotConnectedException) {
                    // Don't report autobugs for this, getting an exception here is expected behavior
                    errormsg("Ending FromNum read, radio not connected", e)
                }
            }
        }
    }

    /**
     * Some buggy BLE stacks can fail on initial connect, with either missing services or missing characteristics.  If that happens we
     * disconnect and try again when the device reenumerates.
     */
    private suspend fun retryDueToException() {
        try {
            /// We gracefully handle safe being null because this can occur if someone has unpaired from our device - just abandon the reconnect attempt
            val s = safe
            if (s != null) {
                reconnectUntilQueued(s)
            } else {
                warn("Abandoning reconnect because safe==null, someone must have closed the device")
            }
        } catch (ex: CancellationException) {
            warn("retryDueToException was cancelled")
        } finally {
            reconnectJob = null
        }
    }

    private suspend fun reconnectUntilQueued(safeBluetooth: SafeBluetooth) {
        while (safe === safeBluetooth) {
            warn("Forcing disconnect and hopefully device will comeback (disabling forced refresh)")
            ignoreException {
                safeBluetooth.resetGattForReconnect()
            }
            service.onDisconnect(false) // assume we will fail
            delay(RECONNECT_DELAY_MILLIS)
            warn("Attempting reconnect")
            if (startConnect()) return
            warn("Reconnect attempt could not be queued, retrying")
        }
        warn("Not connecting, because safe changed or someone must have closed us")
    }

    /// We only try to set MTU once, because some buggy implementations fail
    @Volatile
    private var shouldSetMtu = true

    private fun doDiscoverServicesAndInit() {
        val s = safe
        if (s == null)
            warn("Interface is shutting down, so skipping discover")
        else
            s.asyncDiscoverServices { discRes ->
                try {
                    discRes.getOrThrow()

                    service.serviceScope.handledLaunch {
                        try {
                            debug("Discovered services!")
                            delay(1000) // android BLE is buggy and needs a 500ms sleep before calling getChracteristic, or you might get back null

                            /* if (isFirstTime) {
                                isFirstTime = false
                                throw BLEException("Faking a BLE failure")
                            } */

                            fromNum = getCharacteristic(BTM_FROMNUM_CHARACTER)

                            // We treat the first send by a client as special
                            isFirstSend = true

                            // Now tell clients they can (finally use the api)
                            service.onConnect()

                            // Immediately broadcast any queued packets sitting on the device
                            delay(1000) // Workaround to avoid two connections in a row
                            doReadFromRadio(true)
                        } catch (ex: BLEException) {
                            scheduleReconnect(
                                "Unexpected error in initial device enumeration, forcing disconnect $ex"
                            )
                        }
                    }
                } catch (ex: BLEException) {
                    if (s.gatt == null)
                        warn("GATT was closed while discovering, assume we are shutting down")
                    else
                        scheduleReconnect(
                            "Unexpected error discovering services, forcing disconnect $ex"
                        )
                }
            }
    }

    private fun onConnect(connRes: Result<Unit>) {
        // This callback is invoked after we are connected
        connRes.onFailure {
            scheduleReconnect("connect failed, ${it.message}")
            return
        }

        service.serviceScope.handledLaunch {
            info("Connected to radio!")

            if (needForceRefresh) { // Our ESP32 code doesn't properly generate "service changed" indications.  Therefore we need to force a refresh on initial start
                //needForceRefresh = false // In fact, because of tearing down BLE in sleep on the ESP32, our handle # assignments are not stable across sleep - so we much refetch every time
                forceServiceRefresh() // this article says android should not be caching, but it does on some phones: https://punchthrough.com/attribute-caching-in-ble-advantages-and-pitfalls/

                delay(500) // From looking at the android C code it seems that we need to give some time for the refresh message to reach that worked _before_ we try to set mtu/get services
                // 200ms was not enough on an Amazon Fire
            }

            // we begin by setting our MTU size as high as it can go (if we can)
            if (shouldSetMtu)
                safe?.asyncRequestMtu(512) { mtuRes ->
                    try {
                        mtuRes.getOrThrow()
                        debug("MTU change attempted")

                        // throw BLEException("Test MTU set failed")

                        doDiscoverServicesAndInit()
                    } catch (ex: BLEException) {
                        shouldSetMtu = false
                        scheduleReconnect(
                            "Giving up on setting MTUs, forcing disconnect $ex"
                        )
                    }
                }
            else
                doDiscoverServicesAndInit()
        }
    }


    override fun close() {
        reconnectJob?.cancel() // Cancel any queued reconnect attempts

        if (safe != null) {
            info("Closing BluetoothInterface")
            val s = safe
            safe =
                null // We do this first, because if we throw we still want to mark that we no longer have a valid connection

            try {
                s?.close()
            } catch (_: BLEConnectionClosing) {
                warn("Ignoring BLE errors while closing")
            }
        } else {
            debug("Radio was not connected, skipping disable")
        }
    }

    /// Start a connection attempt
    private fun startConnect(): Boolean {
        // we pass in true for autoconnect - so we will autoconnect whenever the radio
        // comes in range (even if we made this connect call long ago when we got powered on)
        // see https://stackoverflow.com/questions/40156699/which-correct-flag-of-autoconnect-in-connectgatt-of-ble for
        // more info
        val safeBluetooth = safe ?: return false
        return runCatching {
            safeBluetooth.asyncConnect(
                autoConnect = true,
                timeout = CONNECT_ATTEMPT_TIMEOUT_MILLIS,
                cb = ::onConnect,
                lostConnectCb = { scheduleReconnect("connection dropped") },
            )
            true
        }.onFailure {
            warn("Could not start connect, ${it.message}")
        }.getOrDefault(false)
    }


    /**
     * Get a chracteristic, but in a safe manner because some buggy BLE implementations might return null
     */
    private fun getCharacteristic(uuid: UUID) =
        bservice.getCharacteristic(uuid) ?: throw BLECharacteristicNotFoundException(uuid)

}
