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

package com.geeksville.mesh.ui.components.config

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.AlertDialog
import androidx.compose.material.Button
import androidx.compose.material.LinearProgressIndicator
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.emp3r0r7.darkmesh.R
import com.geeksville.mesh.ui.ResponseState
@Composable
fun <T> PacketResponseStateDialog(
    state: ResponseState<T>,
    onDismiss: () -> Unit = {},
    onComplete: () -> Unit = {},
) {
    if (state is ResponseState.Loading) {
        androidx.compose.runtime.LaunchedEffect(state.total, state.completed) {
            if (state.completed >= state.total && state.total > 0) {
                onComplete()
            }
        }
    }

    AlertDialog(
        onDismissRequest = {},
        shape = RoundedCornerShape(16.dp),
        backgroundColor = MaterialTheme.colors.background,
        title = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                when (state) {
                    is ResponseState.Loading -> {
                        val rawProgress = if (state.total > 0) {
                            state.completed.toFloat() / state.total.toFloat()
                        } else 0f

                        val clampedProgress by animateFloatAsState(
                            targetValue = rawProgress.coerceIn(0f, 1f),
                            label = "progress",
                        )

                        Text("${state.completed}/${state.total}")
                        LinearProgressIndicator(
                            progress = clampedProgress,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                            color = MaterialTheme.colors.onSurface,
                        )
                    }

                    is ResponseState.Success -> {
                        Text(text = stringResource(id = R.string.delivery_confirmed))
                    }

                    is ResponseState.Error -> {
                        Text(text = stringResource(id = R.string.error), minLines = 2)
                        Text(text = state.error.asString())
                    }

                    is ResponseState.Empty -> Unit
                }
            }
        },
        buttons = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 24.dp, end = 24.dp, bottom = 16.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.padding(top = 16.dp)
                ) { Text(stringResource(R.string.close)) }
            }
        }
    )
}

@Preview(showBackground = true)
@Composable
private fun PacketResponseStateDialogPreview() {
    PacketResponseStateDialog(
        state = ResponseState.Loading(
            total = 17,
            completed = 5,
        ),
    )
}
