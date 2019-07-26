package org.tensorflow.demo

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.widget.ImageView

class ImageSlider @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : ImageView(context, attrs, defStyleAttr) {

    var value = 0.0f
        set(value) {
            field = value
            postInvalidate()
        }

    private var highlight = false
    private var allZero = false

    private val boxPaint: Paint = Paint().apply {
        color = Color.BLACK
        alpha = 128
    }
    private val linePaint: Paint = Paint().apply {
        color = Color.WHITE
        strokeWidth = 10.0f
        style = Paint.Style.STROKE
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val y = (1.0f - this.value) * height

        // If all sliders are zero, don't bother shading anything.
        if (!allZero) {
            canvas.drawRect(0f, 0f, width.toFloat(), y, boxPaint)
        }

        if (this.value > 0.0f) {
            canvas.drawLine(0f, y, width.toFloat(), y, linePaint)
        }

        if (highlight) {
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), linePaint)
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        setMeasuredDimension(measuredWidth, measuredWidth)
    }

    fun setHighlight(highlight: Boolean) {
        this.highlight = highlight
        postInvalidate()
    }

    fun setAllZero(allZero: Boolean) {
        this.allZero = allZero
    }
}
