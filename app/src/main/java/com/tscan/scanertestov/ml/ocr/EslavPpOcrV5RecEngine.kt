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
import kotlin.math.ln

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

    /**
     * Вероятности символов по шагам (T × классы) — модель уже отдаёт softmax.
     * Нужны для чтения только кириллицей и для проверки слов словаря по самой модели.
     */
    fun recognizeProbs(matBgr: Mat): Array<FloatArray> {
        val sess = session ?: error("Eslav OCR не загружен")
        val inName = inputName ?: error("inputName")
        val outName = outputName ?: error("outputName")
        val (flat, imgW) = PaddleRecPreprocessor.floatChwFromBgrMatChinese(matBgr, REC_IMG_H, 320)
        val tensor = OnnxTensor.createTensor(
            ortEnv!!,
            java.nio.FloatBuffer.wrap(flat),
            longArrayOf(1, 3, REC_IMG_H.toLong(), imgW.toLong()),
        )
        return tensor.use { tin ->
            sess.run(mapOf(inName to tin)).use { r ->
                @Suppress("UNCHECKED_CAST")
                ((r.get(outName).get() as OnnxTensor).value as Array<Array<FloatArray>>)[0]
            }
        }
    }

    private val cyrillicMask: BooleanArray by lazy {
        val letters = "абвгдеёжзийклмнопрстуфхцчшщъыьэюя"
        BooleanArray(characterTable.size) { i ->
            val c = characterTable[i]
            i == 0 || (c.length == 1 && (c[0].lowercaseChar() in letters || c[0] == '-'))
        }
    }

    private val charIndex: Map<Char, Int> by lazy {
        HashMap<Char, Int>().apply {
            characterTable.forEachIndexed { i, c -> if (i > 0 && c.length == 1 && !containsKey(c[0])) put(c[0], i) }
        }
    }

    /** Жадное CTC-чтение, где разрешены только русские буквы и дефис. Возвращает текст и среднюю уверенность. */
    fun greedyCyrillic(probs: Array<FloatArray>): Pair<String, Float> {
        val mask = cyrillicMask
        val sb = StringBuilder()
        var prev = -1
        var sum = 0f
        var n = 0
        for (row in probs) {
            var best = 0
            var bestV = -1f
            for (j in row.indices) if (j < mask.size && mask[j] && row[j] > bestV) { bestV = row[j]; best = j }
            if (best != 0 && best != prev) { sb.append(characterTable[best]); sum += bestV; n++ }
            prev = best
        }
        return sb.toString() to (if (n > 0) sum / n else 0f)
    }

    /** ln P(слово | изображение) по CTC (прямой проход). Очень маленькое число, если символа нет в алфавите модели. */
    fun ctcLogProb(probs: Array<FloatArray>, word: String): Double {
        if (word.isEmpty() || probs.isEmpty()) return -1e9
        val ext = IntArray(word.length * 2 + 1)
        for ((i, ch) in word.withIndex()) ext[2 * i + 1] = charIndex[ch] ?: return -1e9
        val l = ext.size
        val neg = -1e30
        var a = DoubleArray(l) { neg }
        fun lp(t: Int, s: Int) = ln(probs[t][ext[s]].toDouble() + 1e-12)
        a[0] = lp(0, 0)
        a[1] = lp(0, 1)
        for (t in 1 until probs.size) {
            val b = DoubleArray(l)
            for (s in 0 until l) {
                var v = a[s]
                if (s >= 1) v = logAdd(v, a[s - 1])
                if (s >= 2 && ext[s] != 0 && ext[s] != ext[s - 2]) v = logAdd(v, a[s - 2])
                b[s] = v + lp(t, s)
            }
            a = b
        }
        return logAdd(a[l - 1], a[l - 2])
    }

    private fun logAdd(x: Double, y: Double): Double {
        val m = maxOf(x, y)
        return if (m < -1e29) m else m + ln(exp(x - m) + exp(y - m))
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

