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

package com.geeksville.mesh.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.app.Person
import com.emp3r0r7.darkmesh.R
import com.geeksville.mesh.MainActivity
import com.geeksville.mesh.android.notificationManager
import com.geeksville.mesh.database.entity.NodeEntity
import com.geeksville.mesh.util.formatUptime
import org.meshtastic.proto.TelemetryProtos.LocalStats

@Suppress("TooManyFunctions")
class MeshServiceNotifications(
    private val context: Context
) {

    companion object {
        private const val FIFTEEN_MINUTES_IN_MILLIS = 15L * 60 * 1000
        const val OPEN_MESSAGE_ACTION = "com.geeksville.mesh.OPEN_MESSAGE_ACTION"
        const val OPEN_MESSAGE_EXTRA_CONTACT_KEY = "com.geeksville.mesh.OPEN_MESSAGE_EXTRA_CONTACT_KEY"
        private const val CONNECTED_BATTERY_ALERT_CHANNEL_ID = "battery_alerts_connected"
        private const val CONNECTED_CRITICAL_BATTERY_ALERT_CHANNEL_ID = "battery_alerts_connected_critical"
        private const val MESH_BATTERY_ALERT_CHANNEL_ID = "battery_alerts_mesh"
        private const val MESH_CRITICAL_BATTERY_ALERT_CHANNEL_ID = "battery_alerts_mesh_critical"
    }

    private val notificationManager: NotificationManager get() = context.notificationManager

    // We have two notification channels: one for general service status and another one for messages
    val notifyId = 101

    @RequiresApi(Build.VERSION_CODES.O)
    private fun createNotificationChannel(): String {
        val channelId = "my_service"
        val channelName = context.getString(R.string.meshtastic_service_notifications)
        val channel = NotificationChannel(
            channelId,
            channelName,
            NotificationManager.IMPORTANCE_MIN
        ).apply {
            lightColor = Color.BLUE
            lockscreenVisibility = Notification.VISIBILITY_PRIVATE
        }
        notificationManager.createNotificationChannel(channel)
        return channelId
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun createMessageNotificationChannel(): String {
        val channelId = "my_messages"
        val channelName = context.getString(R.string.meshtastic_messages_notifications)
        val channel = NotificationChannel(
            channelId,
            channelName,
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            lightColor = Color.BLUE
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            setShowBadge(true)
            setSound(
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
        }
        notificationManager.createNotificationChannel(channel)
        return channelId
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun createNewNodeNotificationChannel(): String {
        val channelId = "new_nodes"
        val channelName = context.getString(R.string.meshtastic_new_nodes_notifications)
        val channel = NotificationChannel(
            channelId,
            channelName,
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            lightColor = Color.BLUE
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            setShowBadge(true)
            setSound(
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
        }
        notificationManager.createNotificationChannel(channel)
        return channelId
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun createBatteryAlertNotificationChannel(
        source: BatteryAlertSource,
        level: BatteryAlertLevel,
        soundUri: Uri
    ): String {
        val channelId = when (source) {
            BatteryAlertSource.CONNECTED_NODE -> {
                if (level == BatteryAlertLevel.CRITICAL) {
                    CONNECTED_CRITICAL_BATTERY_ALERT_CHANNEL_ID
                } else {
                    CONNECTED_BATTERY_ALERT_CHANNEL_ID
                }
            }
            BatteryAlertSource.MESH -> {
                if (level == BatteryAlertLevel.CRITICAL) {
                    MESH_CRITICAL_BATTERY_ALERT_CHANNEL_ID
                } else {
                    MESH_BATTERY_ALERT_CHANNEL_ID
                }
            }
        }
        val sourceLabel = context.getString(
            if (source == BatteryAlertSource.CONNECTED_NODE) {
                R.string.battery_alert_connected_node_label
            } else {
                R.string.battery_alert_mesh_nodes_label
            }
        )
        val channelName = context.getString(
            if (level == BatteryAlertLevel.CRITICAL) {
                R.string.battery_alert_critical_notification_channel_name
            } else {
                R.string.battery_alert_notification_channel_name
            },
            sourceLabel,
        )
        val channel = NotificationChannel(
            channelId,
            channelName,
            if (level == BatteryAlertLevel.CRITICAL) {
                NotificationManager.IMPORTANCE_HIGH
            } else {
                NotificationManager.IMPORTANCE_DEFAULT
            }
        ).apply {
            lightColor = if (level == BatteryAlertLevel.CRITICAL) Color.RED else Color.YELLOW
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            setShowBadge(true)
            setSound(
                soundUri,
                batteryAlertAudioAttributes
            )
        }
        notificationManager.createNotificationChannel(channel)
        return channelId
    }

    private val channelId: String by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createNotificationChannel()
        } else {
            // If earlier version channel ID is not used
            // https://developer.android.com/reference/android/support/v4/app/NotificationCompat.Builder.html#NotificationCompat.Builder(android.content.Context)
            ""
        }
    }

    private val messageChannelId: String by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createMessageNotificationChannel()
        } else {
            // If earlier version channel ID is not used
            // https://developer.android.com/reference/android/support/v4/app/NotificationCompat.Builder.html#NotificationCompat.Builder(android.content.Context)
            ""
        }
    }

    private val newNodeChannelId: String by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createNewNodeNotificationChannel()
        } else {
            ""
        }
    }

    private val batteryAlertAudioAttributes: AudioAttributes by lazy {
        AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
    }

    private fun LocalStats?.formatToString(): String = this?.allFields?.mapNotNull { (k, v) ->
        when (k.name) {
            "num_online_nodes", "num_total_nodes" -> return@mapNotNull null
            "uptime_seconds" -> "Uptime: ${formatUptime(v as Int)}"
            "channel_utilization" -> "ChUtil: %.2f%%".format(v)
            "air_util_tx" -> "AirUtilTX: %.2f%%".format(v)
            else -> "${
                k.name.replace('_', ' ').split(" ")
                    .joinToString(" ") { it.replaceFirstChar { char -> char.uppercase() } }
            }: $v"
        }
    }?.joinToString("\n") ?: "No Local Stats"

    fun updateServiceStateNotification(
        summaryString: String? = null,
        localStats: LocalStats? = null,
        currentStatsUpdatedAtMillis: Long? = null,
    ) {
        notificationManager.notify(
            notifyId,
            createServiceStateNotification(
                name = summaryString.orEmpty(),
                message = localStats.formatToString(),
                nextUpdateAt = currentStatsUpdatedAtMillis?.plus(FIFTEEN_MINUTES_IN_MILLIS)
            )
        )
    }

    fun updateMessageNotification(contactKey: String, name: String, message: String) =
        notificationManager.notify(
            contactKey.hashCode(), // show unique notifications,
            createMessageNotification(contactKey, name, message)
        )

    fun showNewNodeSeenNotification(node: NodeEntity) {
        notificationManager.notify(
            node.num, // show unique notifications
            createNewNodeSeenNotification(node.user.shortName, node.user.longName)
        )
    }

    fun showLowBatteryNotification(
        node: NodeEntity,
        level: BatteryAlertLevel,
        source: BatteryAlertSource,
        soundUri: String?
    ) {
        notificationManager.notify(
            lowBatteryNotificationId(node.num),
            createLowBatteryNotification(node, level, source, soundUri)
        )
    }

    fun cancelLowBatteryNotification(nodeNum: Int) {
        notificationManager.cancel(lowBatteryNotificationId(nodeNum))
    }

    fun resetBatteryAlertChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        listOf(
            CONNECTED_BATTERY_ALERT_CHANNEL_ID,
            CONNECTED_CRITICAL_BATTERY_ALERT_CHANNEL_ID,
            MESH_BATTERY_ALERT_CHANNEL_ID,
            MESH_CRITICAL_BATTERY_ALERT_CHANNEL_ID,
            "battery_alerts",
            "battery_alerts_critical",
        ).forEach(notificationManager::deleteNotificationChannel)
    }

    private val openAppIntent: PendingIntent by lazy {
        PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private fun openMessageIntent(contactKey: String): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
        intent.action = OPEN_MESSAGE_ACTION
        intent.putExtra(OPEN_MESSAGE_EXTRA_CONTACT_KEY, contactKey)

        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return pendingIntent
    }

    private fun commonBuilder(channel: String): NotificationCompat.Builder {
        val builder = NotificationCompat.Builder(context, channel)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(openAppIntent)

        // Set the notification icon
        if (Build.VERSION.SDK_INT <= 24) { //sotto android 7 (Nougat)
            // If running on really old versions of android (<= 5.1.1) (possibly only cyanogen) we might encounter a bug with setting application specific icons
            // so punt and stay with just the bluetooth icon - see https://meshtastic.discourse.group/t/android-1-1-42-ready-for-alpha-testing/2399/3?u=geeksville
            builder.setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
        } else {
            builder.setSmallIcon(
                // vector form icons don't work reliably on older androids
                R.drawable.skull_icon_ver2
            )
        }
        return builder
    }

    lateinit var serviceNotificationBuilder: NotificationCompat.Builder
    fun createServiceStateNotification(
        name: String,
        message: String? = null,
        nextUpdateAt: Long? = null
    ): Notification {
        if (!::serviceNotificationBuilder.isInitialized) {
            serviceNotificationBuilder = commonBuilder(channelId)
        }
        with(serviceNotificationBuilder) {
            priority = NotificationCompat.PRIORITY_MIN
            setCategory(Notification.CATEGORY_SERVICE)
            setOngoing(true)
            setContentTitle(name)
            message?.let {
                setContentText(it)
                setStyle(
                    NotificationCompat.BigTextStyle()
                        .bigText(message),
                )
            }
            nextUpdateAt?.let {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    setWhen(it)
                    setUsesChronometer(true)
                    setChronometerCountDown(true)
                }
            } ?: {
                setWhen(System.currentTimeMillis())
            }
            setShowWhen(true)
        }
        return serviceNotificationBuilder.build()
    }

    lateinit var messageNotificationBuilder: NotificationCompat.Builder
    private fun createMessageNotification(contactKey: String, name: String, message: String): Notification {
        if (!::messageNotificationBuilder.isInitialized) {
            messageNotificationBuilder = commonBuilder(messageChannelId)
        }
        val person = Person.Builder().setName(name).build()
        with(messageNotificationBuilder) {
            setContentIntent(openMessageIntent(contactKey))
            priority = NotificationCompat.PRIORITY_DEFAULT
            setCategory(Notification.CATEGORY_MESSAGE)
            setAutoCancel(true)
            setStyle(
                NotificationCompat.MessagingStyle(person).addMessage(message, System.currentTimeMillis(), person)
            )
        }
        return messageNotificationBuilder.build()
    }

    lateinit var newNodeSeenNotificationBuilder: NotificationCompat.Builder
    private fun createNewNodeSeenNotification(name: String, message: String? = null): Notification {
        if (!::newNodeSeenNotificationBuilder.isInitialized) {
            newNodeSeenNotificationBuilder = commonBuilder(newNodeChannelId)
        }
        with(newNodeSeenNotificationBuilder) {
            priority = NotificationCompat.PRIORITY_DEFAULT
            setCategory(Notification.CATEGORY_STATUS)
            setAutoCancel(true)
            setContentTitle("New Node Seen: $name")
            message?.let {
                setContentText(it)
                setStyle(
                    NotificationCompat.BigTextStyle()
                        .bigText(message),
                )
            }
            setWhen(System.currentTimeMillis())
            setShowWhen(true)
        }
        return newNodeSeenNotificationBuilder.build()
    }

    private fun lowBatteryNotificationId(nodeNum: Int): Int = "battery_alert_$nodeNum".hashCode()

    private fun batteryAlertSoundUri(soundUri: String?): Uri =
        soundUri?.takeUnless { it.isBlank() }?.let(Uri::parse)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

    private fun createLowBatteryNotification(
        node: NodeEntity,
        level: BatteryAlertLevel,
        source: BatteryAlertSource,
        soundUri: String?
    ): Notification {
        val notificationSoundUri = batteryAlertSoundUri(soundUri)
        val channelId = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createBatteryAlertNotificationChannel(source, level, notificationSoundUri)
        } else {
            ""
        }
        val nodeName = node.user.longName.takeUnless { it.isNullOrBlank() }
            ?: node.user.shortName.takeUnless { it.isNullOrBlank() }
            ?: context.getString(R.string.battery_alert_unknown_node)
        val metrics = node.deviceMetrics
        val message = when {
            metrics.batteryLevel in 1..100 && metrics.voltage > 0f -> context.getString(
                R.string.battery_alert_percent_and_voltage,
                nodeName,
                metrics.batteryLevel,
                metrics.voltage,
            )
            metrics.batteryLevel in 1..100 -> context.getString(
                R.string.battery_alert_percent_only,
                nodeName,
                metrics.batteryLevel,
            )
            metrics.voltage > 0f -> context.getString(
                R.string.battery_alert_voltage_only,
                nodeName,
                metrics.voltage,
            )
            else -> nodeName
        }

        return commonBuilder(channelId).apply {
            priority = if (level == BatteryAlertLevel.CRITICAL) {
                NotificationCompat.PRIORITY_HIGH
            } else {
                NotificationCompat.PRIORITY_DEFAULT
            }
            setCategory(Notification.CATEGORY_STATUS)
            setAutoCancel(true)
            setSmallIcon(R.drawable.ic_battery_alert)
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                setSound(notificationSoundUri)
            }
            setContentTitle(
                context.getString(
                    if (level == BatteryAlertLevel.CRITICAL) {
                        R.string.battery_alert_critical_title
                    } else {
                        R.string.battery_alert_low_title
                    },
                    nodeName,
                )
            )
            setContentText(message)
            setStyle(NotificationCompat.BigTextStyle().bigText(message))
            setWhen(System.currentTimeMillis())
            setShowWhen(true)
        }.build()
    }
}
