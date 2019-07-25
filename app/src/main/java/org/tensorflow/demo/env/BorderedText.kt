/* Copyright 2016 The TensorFlow Authors. All Rights Reserved.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
==============================================================================*/

package org.tensorflow.demo.env

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Paint.Style
import android.graphics.Typeface

class BorderedText(private val textSize: Float) {

    private val interiorPaint: Paint = Paint().apply {
        textSize = textSize
        color = Color.WHITE
        style = Style.FILL
        isAntiAlias = false
        alpha = 255
    }

    private val exteriorPaint: Paint = Paint().apply {
        textSize = textSize
        color = Color.BLACK
        style = Style.FILL_AND_STROKE
        strokeWidth = textSize / 8
        isAntiAlias = false
        alpha = 255
    }

    fun setTypeface(typeface: Typeface) {
        interiorPaint.typeface = typeface
        exteriorPaint.typeface = typeface
    }

    fun drawText(canvas: Canvas, startX: Float, startY: Float, lines: List<String>) {
        for ((lineNum, line) in lines.withIndex()) {
            drawText(canvas, startX, startY - textSize * (lines.size - lineNum - 1), line)
        }
    }

    private fun drawText(canvas: Canvas, posX: Float, posY: Float, text: String) {
        canvas.drawText(text, posX, posY, exteriorPaint)
        canvas.drawText(text, posX, posY, interiorPaint)
    }
}
