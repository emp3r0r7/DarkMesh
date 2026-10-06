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

package com.geeksville.mesh.database.entity

object DiscoverySessionStatus {
    const val IN_PROGRESS = "in_progress"
    const val INTERRUPTED = "interrupted"
    const val COMPLETE = "complete"
    const val FAILED = "failed"
    const val STOPPED = "stopped"
    const val RESTORED = "restored"
    const val UNRESTORABLE = "unrestorable"
    const val RESTORE_PENDING = "restore_pending"
    const val RESTORE_FAILED = "restore_failed"

    val recoverable = setOf(
        IN_PROGRESS,
        INTERRUPTED,
        FAILED,
        STOPPED,
        RESTORE_PENDING,
        RESTORE_FAILED,
    )
}
