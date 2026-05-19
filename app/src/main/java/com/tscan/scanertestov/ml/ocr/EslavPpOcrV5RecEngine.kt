package com.tscan.scanertestov.ml.ocr

/**
 * Описание: распознавание одной строки на восточнославянских языках (модель eslav_PP-OCRv5).
 */
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.util.Log
import org.opencv.android.OpenCVLoader
import org.opencv.core.Mat
import java.io.File
import kotlin.math.exp

object EslavPpOcrV5RecEngine {
    private const val TAG = "EslavPpOcrV5Rec"
    private const val ASSET_MODEL = "models/ocr/eslav_PP-OCRv5_mobile_rec/model.onnx"
    private const val ASSET_DICT = "models/ocr/eslav_PP-OCRv5_mobile_rec/dict.txt"
    private const val CACHE_MODEL = "eslav_ppocrv5_mobile_rec_cached.onnx"
    private const val REC_IMG_H = 48

    private val lock = Any()

    @Volatile
    private var ortEnv: OrtEnvironment? = null

    @Volatile
    private var session: OrtSession? = null

    @Volatile
    private var inputName: String? = null

    @Volatile
    private var outputName: String? = null

    @Volatile
    private var characterTable: List<String> = emptyList()

    fun ensureLoaded(context: Context): String? {
        if (session != null && characterTable.isNotEmpty()) return null
        synchronized(lock) {
            if (session != null && characterTable.isNotEmpty()) return null
            return try {
                if (!OpenCVLoader.initLocal()) return "Не удалось инициализировать OpenCV"
                val app = context.applicationContext
                characterTable = buildCharacterTable(app)
                ortEnv = OrtEnvironment.getEnvironment()
                val modelFile = copyAssetToCache(app, ASSET_MODEL, CACHE_MODEL)
                val opts = OrtSession.SessionOptions().apply {
                    setIntraOpNumThreads(2)
                    setInterOpNumThreads(1)
                    setOptimizationLevel(OrtSession.SessionOptions.OptLevel.BASIC_OPT)
                }
                val sess = ortEnv!!.createSession(modelFile.absolutePath, opts)
                session = sess
                inputName = sess.inputNames.first()
                outputName = sess.outputNames.first()
                Log.i(TAG, "Eslav OCR loaded: in=$inputName out=$outputName classes=${characterTable.size}")
                null
            } catch (e: Exception) {
                Log.e(TAG, "Ошибка загрузки eslav OCR", e)
                e.message ?: e.toString()
            }
        }
    }

    fun recognizeLine(matBgr: Mat): Pair<String, Float> {
        val sess = session ?: error("Eslav OCR не загружен")
        val inName = inputName ?: error("inputName")
        val outName = outputName ?: error("outputName")
        val table = characterTable
        val (flat, imgW) = PaddleRecPreprocessor.floatChwFromBgrMatChinese(
            src = matBgr,
            imgH = REC_IMG_H,
            imgWBase = 320,
        )
        val nested = nestedArrayFromChw(flat, REC_IMG_H, imgW)
        val env = ortEnv!!
        val tensor = OnnxTensor.createTensor(env, nested)
        return tensor.use { tin ->
            val feeds = mapOf(inName to tin)
            val result = sess.run(feeds)
            result.use { r ->
                val onnxTensor = r.get(outName).get() as OnnxTensor
                @Suppress("UNCHECKED_CAST")
                val batch = onnxTensor.value as Array<Array<FloatArray>>
                val seq = batch[0]
                val (indices, probs) = argmaxWithSoftmaxConfidence(seq)
                ctcDecode(indices, probs, table)
            }
        }
    }

    private fun buildCharacterTable(context: Context): List<String> {
        val lines = context.assets.open(ASSET_DICT).bufferedReader(Charsets.UTF_8).readLines()
            .map { it.trimEnd('\r') }
        val fromFile = lines.toMutableList()
        fromFile.add(" ")
        return listOf("blank") + fromFile
    }

    private fun copyAssetToCache(context: Context, assetPath: String, cacheName: String): File {
        val out = File(context.cacheDir, cacheName)
        context.assets.open(assetPath).use { input ->
            out.outputStream().use { output -> input.copyTo(output) }
        }
        return out
    }

    private fun nestedArrayFromChw(flat: FloatArray, h: Int, w: Int): Array<Array<Array<FloatArray>>> {
        return Array(1) {
            Array(3) { c ->
                Array(h) { y ->
                    FloatArray(w) { x ->
                        flat[c * h * w + y * w + x]
                    }
                }
            }
        }
    }

    private fun argmaxWithSoftmaxConfidence(seq: Array<FloatArray>): Pair<IntArray, FloatArray> {
        val t = seq.size
        val classes = seq[0].size
        val indices = IntArray(t)
        val confs = FloatArray(t)
        val buf = DoubleArray(classes)
        for (i in 0 until t) {
            val row = seq[i]
            var maxJ = 0
            var maxV = row[0].toDouble()
            for (j in 1 until classes) {
                val v = row[j].toDouble()
                if (v > maxV) {
                    maxV = v
                    maxJ = j
                }
            }
            var sum = 0.0
            for (j in 0 until classes) {
                buf[j] = exp(row[j].toDouble() - maxV)
                sum += buf[j]
            }
            val p = (buf[maxJ] / sum).toFloat()
            indices[i] = maxJ
            confs[i] = p
        }
        return indices to confs
    }

    private fun ctcDecode(indices: IntArray, probs: FloatArray, table: List<String>): Pair<String, Float> {
        val ignored = setOf(0)
        val selection = BooleanArray(indices.size) { true }
        for (i in 1 until indices.size) {
            if (indices[i] == indices[i - 1]) selection[i] = false
        }
        for (i in indices.indices) {
            if (indices[i] in ignored) selection[i] = false
        }
        val chars = StringBuilder()
        var confSum = 0f
        var n = 0
        for (i in indices.indices) {
            if (!selection[i]) continue
            val id = indices[i]
            if (id < 0 || id >= table.size) continue
            val ch = table[id]
            if (ch != "blank") {
                chars.append(ch)
                confSum += probs[i]
                n++
            }
        }
        val mean = if (n > 0) confSum / n else 0f
        return chars.toString() to mean
    }
}

