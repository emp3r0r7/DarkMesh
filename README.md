<h1 align="center">DarkMesh-Android</h1>

<p align="center">
  An independent development from Meshtastic LLC. focused on Privacy and Field Operators.
  Licensed under <b>GPL-3.0</b>.
</p>

<p align="center">
The Android application and the Darkmesh firmware are provided without any guarantee of operation or reliability, neither express nor implied. Use takes place under the user’s sole responsibility. The author of the software declines all responsibility for any violations of regulations, as well as for direct, indirect, incidental, or consequential damages arising from the use, misuse, or inability to use the software.
Use of the software entails the implicit acceptance of the conditions indicated above.
</p>

---

## 🛰️ Overview

**DarkMesh** is an independent fork and autonomous development of the **Meshtastic** project,  
focused on **scheduled messaging** and **metadata forwarding** to **online mapping platforms**.

Designed for **advanced users** and **field operators**, DarkMesh introduces a range of  
new features to enhance decentralized networking and node-to-node interaction  
in tactical or remote scenarios.

---

## ⚙️ Core Features

### 🔭 Hunting Mode
Streams real-time telemetry from mesh nodes to a **remote server**  
for display on a **dynamic online map**.  
Ideal for tracking, live monitoring, or operational coordination.  
[_A short setup guide is available here!_](https://mesh.loracity.it/mappa-dei-nodi-meshtastic-ricevuti-via-radio/)

### 🕒 Message Scheduling
Plan and schedule **private** or **group** messages to be automatically  
sent at specific times.  
Useful for automations, time-based alerts, or mission coordination.

### 📡 Beaconing
Send **periodic beacons** with customizable text payloads.  
Beacons can be used for **automatic identification**, **status reporting**,  
or **node discovery** within the network.

### 📡 Traceroute Map visualization
It is possibile to visualize traceroutes on the application map. Forward route and Backward Route.
It is also shown the total distance of the roundtrip.

---

## 🔍 Extended Verbosity and Transparency

DarkMesh offers **enhanced network transparency** with real-time user feedback.

The app displays **toast notifications** whenever:
- a message is **retransmitted** across the mesh, or  
- a **trace request** targets your node.

This behavior provides full awareness of ongoing network events and  
makes debugging or situational analysis far more intuitive.

---

## 🧩 Distinctive Firmware & Release Notes

**Release artifacts** will include firmware binaries for HELTECv3 devices (currently distributed as a custom firmware).  
The firmware used as base is **Meshtastic v2.5.20** and has been modified to implement DarkMesh-specific operational modes and optimizations.

### Firmware variants included in releases
- **Custom HELTECv3 firmware** (Released on private request).
- Builds are derived from Meshtastic **2.5.20** with DarkMesh patches applied.

### Sensor Mode — STEALTH / ANONYMOUS operation
A dedicated runtime mode intended for maximum discretion and minimal airtime. Key behaviors:
- **Telemetry & node metadata suppressed**: In SENSOR mode the node **does not** transmit node info, telemetry, or any periodic metadata messages.
- **Traceroute disabled**: The node will **not** respond to traceroute requests nor will it originate traceroute responses.
- **Message transmit limited to configured channels**: The node can **only** send messages on pre-configured channel(s) (i.e., group topics). This reduces airtime and exposure.
- **Receive capability preserved**: The node can receive all messages addressed to channels it listens to, allowing silent monitoring while avoiding outbound telemetry.
- **Ephemeral node ID**: On every reboot the node's **node ID is randomized/changed**, providing a layer of anonymity between sessions.
- **Behavioral summary**: SENSOR mode = receive-only visibility for telemetry + highly restricted transmit (configured channels only) + no identifying or routing metadata emitted.

> **Intended use:** covert field ops, low-profile monitoring nodes, and scenarios where transmission of identifying metadata must be avoided.

### Client HIDDEN Mode — serial sniffer / passive capture
A client-side mode designed for serial-connected capture and offline analysis:
- When configured as **Client HIDDEN**, the node **forwards every received packet** to the serial interface regardless of whether the packet would be processed by a normal client.
- This mode effectively turns the device into a **passive sniffer** that outputs raw/decoded packets to serial for an attached logger or downstream processor.
- **Use case:** connect the device via serial to a host (e.g., laptop or embedded logger) to capture all mesh traffic for forensic analysis, long-term logging, or to feed custom decoders.

### Other firmware improvements
- **Atomic database writes for ESP32-S3**: improved reliability of on-device persistent storage through atomic write semantics, reducing corruption risk on power failure or concurrent access.
- General robustness and platform-specific semaphore/lock fixes to improve stability on constrained hardware.

---

## ⚠️ Operational Notes & Security Considerations

- **Anonymity limitations:** While SENSOR mode reduces exposure (no telemetry, ephemeral node IDs), it does **not** make the node completely anonymous. RF fingerprinting, timing analysis, or gateway correlation can still deanonymize signals in some threat models. Use appropriate OPSEC and understand the local legal/regulatory context.
- **Testing:** Validate behavior in controlled environments before deploying to production/field. Confirm ephemeral-ID behavior and channel restrictions match your operational needs.
- **Backup:** Always keep a backup of device configuration and keys before flashing custom firmware.
---

## 🧩 License

This project is released under the terms of the **GNU General Public License v3.0 (GPL-3.0)**.  
See the [LICENSE](LICENSE) file for details.

---

## 🧠 Acknowledgments

DarkMesh is based on the open-source **Meshtastic** ecosystem.  
All credit for the base protocol, APIs, and firmware design goes to  
the [Meshtastic](https://github.com/meshtastic) community and its contributors.

---

<p align="center">
  <i>DarkMesh — decentralized communication, refined for the field.</i>
</p>
