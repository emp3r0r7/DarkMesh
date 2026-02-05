package com.geeksville.mesh.ui.share

import android.content.ClipData
import android.graphics.Bitmap
import android.net.Uri
import android.util.Base64
import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.emp3r0r7.darkmesh.R
import com.geeksville.mesh.model.Node
import com.geeksville.mesh.ui.components.SimpleAlertDialog
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.google.zxing.WriterException
import com.journeyapps.barcodescanner.BarcodeEncoder
import kotlinx.coroutines.launch
import org.meshtastic.proto.AdminProtos
import java.net.MalformedURLException


private const val BARCODE_PIXEL_SIZE = 960
private const val MESHTASTIC_HOST = "meshtastic.org"
private const val CONTACT_SHARE_PATH = "/v/"

/** Prefix for Meshtastic contact sharing URLs. */
internal const val URL_PREFIX = "https://$MESHTASTIC_HOST$CONTACT_SHARE_PATH#"
private const val BASE64FLAGS = Base64.URL_SAFE + Base64.NO_WRAP + Base64.NO_PADDING
private const val CAMERA_ID = 0


/** Converts a [AdminProtos.SharedContact] to its corresponding URI representation. */
fun AdminProtos.SharedContact.getSharedContactUrl(): Uri {
    val bytes = this.toByteArray()
    val enc = Base64.encodeToString(bytes, BASE64FLAGS)
    return "$URL_PREFIX$enc".toUri()
}

@Suppress("MagicNumber")
@Throws(MalformedURLException::class)
fun Uri.toSharedContact(): AdminProtos.SharedContact {
    if (
        fragment.isNullOrBlank() ||
        !host.equals(MESHTASTIC_HOST, true) ||
        !path.equals(CONTACT_SHARE_PATH, true)
    ) {
        throw MalformedURLException("Not a valid Meshtastic URL: ${toString().take(40)}")
    }

    val bytes = Base64.decode(
        fragment!!,
        BASE64FLAGS
    )

    return AdminProtos.SharedContact.parseFrom(bytes)
}

@Composable
fun SharedContactDialog(contact: Node?, onDismiss: () -> Unit) {
    if (contact == null) return

    val sharedContact = AdminProtos.SharedContact
        .newBuilder()
        .setUser(contact.user)
        .setNodeNum(contact.num)
        .build()

    val uri = sharedContact.getSharedContactUrl()
    SimpleAlertDialog(
        title = R.string.share,
        text = {
            Column {
                Text(
                    text = contact.user.longName,
                    color = Color.Cyan,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),

                    textAlign = TextAlign.Center
                )
                SharedContact(contactUri = uri)
            }
        },
        onDismiss = onDismiss,
    )
}

/** Bitmap representation of the Uri as a QR code, or null if generation fails. */
val Uri.qrCode: Bitmap?
    get() =
        try {
            val multiFormatWriter = MultiFormatWriter()
            val bitMatrix =
                multiFormatWriter.encode(this.toString(), BarcodeFormat.QR_CODE,
                    BARCODE_PIXEL_SIZE,
                    BARCODE_PIXEL_SIZE
                )
            val barcodeEncoder = BarcodeEncoder()
            barcodeEncoder.createBitmap(bitMatrix)
        } catch (ex: WriterException) {
            Log.e(this.javaClass.simpleName, "URL was too complex to render as barcode: ${ex.message}")
            null
        }

@Composable
private fun SharedContact(contactUri: Uri) {
    Column {
        QrCodeImage(
            uri = contactUri,
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
        )
        Row(modifier = Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = contactUri.toString(),
                color = Color.Cyan,
                modifier = Modifier.weight(1f)
            )
            CopyIconButton(
                valueToCopy = contactUri.toString(),
                modifier = Modifier.padding(start = 8.dp)
            )
        }
    }
}

@Composable
private fun QrCodeImage(uri: Uri, modifier: Modifier = Modifier) = Image(
    painter = uri.qrCode?.let { BitmapPainter(it.asImageBitmap()) } ?: painterResource(id = R.drawable.qrcode),
    contentDescription = "qr code",
    modifier = modifier,
    contentScale = ContentScale.Inside,
)

@Composable
fun CopyIconButton(
    valueToCopy: String,
    modifier: Modifier = Modifier,
    label: String = "Copy",
) {
    val clipboardManager = LocalClipboard.current
    val coroutineScope = rememberCoroutineScope()
    IconButton(
        colors = IconButtonDefaults.iconButtonColors(
            containerColor = Color.Cyan.copy(alpha = 0.15f),
            contentColor = Color.Cyan
        ),
        modifier = modifier,
        onClick = {
            coroutineScope.launch {
                val clipData = ClipData.newPlainText(label, valueToCopy)
                val clipEntry = ClipEntry(clipData)
                clipboardManager.setClipEntry(clipEntry)
            }
        },
    ) {
        Icon(imageVector = Icons.TwoTone.ContentCopy, contentDescription = label)
    }
}
