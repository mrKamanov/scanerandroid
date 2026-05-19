package com.tscan.scanertestov.feature.batch.engine

/**
 * Описание: ONNX-классификатор OMR-ячейки (no/yes/fixed).
 */
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.util.Log
import com.tscan.scanertestov.data.InferenceModelVariant
import com.tscan.scanertestov.data.AppSettingsStore
import org.opencv.core.Mat
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import java.io.File
import java.nio.FloatBuffer
import kotlin.math.exp

internal object BatchCellMlClassifier {
    private const val TAG = "BatchCellMlClassifier"

    private val lock = Any()
    private val missingAssets = mutableSetOf<String>()

    @Volatile
    private var env: OrtEnvironment? = null

    @Volatile
    private var session: OrtSession? = null

    @Volatile
    private var inputName: String? = null

    @Volatile
    private var outputName: String? = null

    @Volatile
    private var activeVariant: InferenceModelVariant? = null

    fun classify(cellBgr: Mat, context: Context): Pair<BatchCellClass, Float>? =
        classifyBatch(listOf(cellBgr), context)?.firstOrNull()

    fun classifyBatch(cells: List<Mat>, context: Context): List<Pair<BatchCellClass, Float>?>? {
        if (cells.isEmpty()) return emptyList()
        val app = context.applicationContext
        val variant = AppSettingsStore.getInferenceModel(app)
        val confidenceThreshold = AppSettingsStore.getOmrMlConfidenceThreshold(app)
        if (!ensureLoaded(app, variant)) return null

        val inputSize = variant.inputSize
        val chw = FloatArray(3 * inputSize * inputSize)
        val resized = Mat()
        val out = ArrayList<Pair<BatchCellClass, Float>?>(cells.size)
        try {
            synchronized(lock) {
                val localSession = session ?: return null
                val inName = inputName ?: return null
                val outName = outputName ?: return null
                val localEnv = env ?: return null
                for (cell in cells) {
                    out += try {
                        Imgproc.resize(cell, resized, Size(inputSize.toDouble(), inputSize.toDouble()))
                        preprocessBgrToChw(resized, inputSize, chw)
                        val tensor = OnnxTensor.createTensor(
                            localEnv,
                            FloatBuffer.wrap(chw),
                            longArrayOf(1, 3, inputSize.toLong(), inputSize.toLong()),
                        )
                        tensor.use { tin ->
                            localSession.run(mapOf(inName to tin)).use { result ->
                                val raw = (result.get(outName).get() as OnnxTensor).value
                                val logits = when (raw) {
                                    is Array<*> -> (raw.firstOrNull() as? FloatArray) ?: floatArrayOf(1f, 0f, 0f)
                                    is FloatArray -> raw
                                    else -> floatArrayOf(1f, 0f, 0f)
                                }
                                val probs = softmax(logits)
                                probsToClass(probs, confidenceThreshold)
                            }
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Сбой инференса ячейки: ${e.message}")
                        null
                    }
                }
            }
        } finally {
            resized.release()
        }
        return out
    }

    private fun probsToClass(probs: FloatArray, confidenceThreshold: Float): Pair<BatchCellClass, Float> {
        val maxIndex = probs.indices.maxByOrNull { probs[it] } ?: 0
        val confidence = probs[maxIndex]
        return if (confidence < confidenceThreshold) {
            BatchCellClass.Fixed to confidence
        } else {
            when (maxIndex) {
                1 -> BatchCellClass.Yes to confidence
                2 -> BatchCellClass.Fixed to confidence
                else -> BatchCellClass.No to confidence
            }
        }
    }

    private fun ensureLoaded(context: Context, variant: InferenceModelVariant): Boolean {
        if (activeVariant == variant && session != null && inputName != null && outputName != null) return true
        synchronized(lock) {
            if (activeVariant == variant && session != null && inputName != null && outputName != null) return true
            if (variant.assetRelativePath in missingAssets) return false
            return try {
                val cachedModel = copyAssetToCache(context, variant.assetRelativePath, variant)
                val localEnv = env ?: OrtEnvironment.getEnvironment().also { env = it }
                val opts = OrtSession.SessionOptions().apply {
                    setIntraOpNumThreads(2)
                    setInterOpNumThreads(1)
                    setOptimizationLevel(OrtSession.SessionOptions.OptLevel.BASIC_OPT)
                }
                session?.close()
                val newSession = localEnv.createSession(cachedModel.absolutePath, opts)
                session = newSession
                inputName = newSession.inputNames.first()
                outputName = newSession.outputNames.first()
                activeVariant = variant
                Log.i(TAG, "OMR ML loaded: ${variant.name} (${variant.inputSize}x${variant.inputSize})")
                true
            } catch (e: Exception) {
                if (e is java.io.FileNotFoundException) {
                    missingAssets += variant.assetRelativePath
                }
                Log.w(TAG, "Не удалось загрузить OMR ONNX (${variant.assetRelativePath}): ${e.message}")
                false
            }
        }
    }

    private fun preprocessBgrToChw(bgr: Mat, inputSize: Int, out: FloatArray) {
        val total = inputSize * inputSize
        val bytes = ByteArray(total * 3)
        bgr.get(0, 0, bytes)
        var offset = 0
        for (idx in 0 until total) {
            val b = (bytes[offset].toInt() and 0xFF) / 255f
            val g = (bytes[offset + 1].toInt() and 0xFF) / 255f
            val r = (bytes[offset + 2].toInt() and 0xFF) / 255f
            offset += 3
            val index2 = idx
            out[index2] = (r - 0.485f) / 0.229f
            out[index2 + total] = (g - 0.456f) / 0.224f
            out[index2 + 2 * total] = (b - 0.406f) / 0.225f
        }
    }

    private fun copyAssetToCache(context: Context, assetPath: String, variant: InferenceModelVariant): File {
        val fileName = "batch_omr_${variant.storageValue}_cached.onnx"
        val out = File(context.cacheDir, fileName)
        context.assets.open(assetPath).use { input ->
            out.outputStream().use { output -> input.copyTo(output) }
        }
        return out
    }

    private fun softmax(logits: FloatArray): FloatArray {
        if (logits.isEmpty()) return floatArrayOf(1f, 0f, 0f)
        val max = logits.maxOrNull() ?: 0f
        val exps = logits.map { exp((it - max).toDouble()).toFloat() }
        val sum = exps.sum().coerceAtLeast(1e-6f)
        return exps.map { it / sum }.toFloatArray()
    }
}
