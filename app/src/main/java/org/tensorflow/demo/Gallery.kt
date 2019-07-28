package org.tensorflow.demo

import android.app.Activity
import android.app.Activity.RESULT_OK
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Environment
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date

object Gallery {

    private const val REQUEST_GET_SINGLE_FILE = 1

    fun takePicture(activity: Activity) {
        val intent = Intent(Intent.ACTION_GET_CONTENT)
        intent.addCategory(Intent.CATEGORY_OPENABLE)
        intent.type = "image/*"
        activity.startActivityForResult(
            Intent.createChooser(intent, "Select Picture"),
            REQUEST_GET_SINGLE_FILE
        )
    }

    fun onPictureTaken(requestCode: Int, resultCode: Int, data: Intent?, callback: (Uri) -> Unit) {
        if (requestCode == REQUEST_GET_SINGLE_FILE && resultCode == RESULT_OK) {
            data?.data?.run(callback)
        }
    }

    private const val SCREENSHOTS_DIR_NAME = "Stylizes"
    private const val SCREENSHOT_FILE_NAME_TEMPLATE = "Stylize_%s.png"

    fun saveBitmap(bitmap: Bitmap): Boolean {
        val imageTime = System.currentTimeMillis()
        val imageDate = SimpleDateFormat("yyyyMMdd-HHmmss").format(Date(imageTime))
        val imageFileName = String.format(SCREENSHOT_FILE_NAME_TEMPLATE, imageDate)

        val directory = File(Environment.getExternalStorageDirectory(), SCREENSHOTS_DIR_NAME)
        val imageFilePath = File(directory, imageFileName).absolutePath

        try {
            directory.mkdirs()

            val out = FileOutputStream(imageFilePath)
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            out.flush()
            out.close()
            return true
        } catch (e: IOException) {
            return false
        }
    }
}
