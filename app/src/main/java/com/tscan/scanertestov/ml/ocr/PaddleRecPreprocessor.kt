package com.tscan.scanertestov.ml.ocr

/**
 * Описание: подготовка изображения строки для входа модели PaddleOCR recognition.
 */
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import kotlin.math.ceil
import kotlin.math.max

object PaddleRecPreprocessor {

    fun floatChwFromBgrMatChinese(
        src: Mat,
        imgH: Int = 48,
        imgWBase: Int = 320
    ): Pair<FloatArray, Int> {
        val imgC = 3
        val h = src.rows()
        val w = src.cols()
        var maxWhRatio = imgWBase * 1.0 / imgH
        val ratio = w * 1.0 / h
        maxWhRatio = max(maxWhRatio, ratio)
        val imgW = (imgH * maxWhRatio).toInt()
        val resizedW = if (ceil(imgH * ratio) > imgW) {
            imgW
        } else {
            ceil(imgH * ratio).toInt()
        }
        val resized = Mat()
        Imgproc.resize(
            src,
            resized,
            Size(resizedW.toDouble(), imgH.toDouble()),
            0.0,
            0.0,
            Imgproc.INTER_LINEAR
        )
        resized.convertTo(resized, CvType.CV_32F, 1.0 / 255.0)
        Core.subtract(resized, Scalar(0.5, 0.5, 0.5), resized)
        Core.divide(resized, Scalar(0.5, 0.5, 0.5), resized)

        val flat = FloatArray(imgC * imgH * imgW)
        val px = FloatArray(3)
        for (y in 0 until imgH) {
            for (x in 0 until resizedW) {
                resized.get(y, x, px)
                for (c in 0 until imgC) {
                    flat[c * imgH * imgW + y * imgW + x] = px[c]
                }
            }
        }
        resized.release()
        return flat to imgW
    }
}
