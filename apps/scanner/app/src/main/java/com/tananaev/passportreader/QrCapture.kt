package com.tananaev.passportreader

import android.graphics.Bitmap
import com.google.zxing.BinaryBitmap
import com.google.zxing.MultiFormatReader
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer

/**
 * §6.2 item 8 cross-device QR fallback — decode only (zxing-core has no
 * Android camera/UI dependency). The scanning UI is the SYSTEM camera app
 * (`MediaStore.ACTION_IMAGE_CAPTURE`, launched from MainActivity), not a
 * live in-app preview — see MainActivity / README.md for why (avoids the
 * CameraX + zxing-android-embedded dependency footprint for a fallback
 * path). One still photo of the verifier's on-screen QR is decoded here.
 */
object QrCapture {

    /** Returns the decoded text, or null if no QR code was found in [bitmap]. */
    fun decode(bitmap: Bitmap): String? {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        val source = RGBLuminanceSource(width, height, pixels)
        val binary = BinaryBitmap(HybridBinarizer(source))
        return try {
            MultiFormatReader().decode(binary).text
        } catch (e: Exception) {
            null // not found / not decodable — a normal outcome, not an error
        }
    }
}
