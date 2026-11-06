package io.github.xxtvrxx233.wearcompass.data

import android.content.Context
import kotlin.math.abs
import kotlin.math.sqrt

data class MagCalibrationResult(
    val offsetX: Float,
    val offsetY: Float,
    val offsetZ: Float,
    val strength: Float,
    val octants: Int,
)

class MagCalibrationStore(context: Context) {

    private val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var offsetX: Float = preferences.getFloat(KEY_OFFSET_X, 0f)
        private set

    var offsetY: Float = preferences.getFloat(KEY_OFFSET_Y, 0f)
        private set

    var offsetZ: Float = preferences.getFloat(KEY_OFFSET_Z, 0f)
        private set

    var expectedStrength: Float = preferences.getFloat(KEY_STRENGTH, 0f)
        private set

    val hasCalibration: Boolean
        get() = expectedStrength > 0f

    fun apply(x: Float, y: Float, z: Float): FloatArray =
        floatArrayOf(x - offsetX, y - offsetY, z - offsetZ)

    fun save(result: MagCalibrationResult) {
        offsetX = result.offsetX
        offsetY = result.offsetY
        offsetZ = result.offsetZ
        expectedStrength = result.strength
        preferences.edit()
            .putFloat(KEY_OFFSET_X, result.offsetX)
            .putFloat(KEY_OFFSET_Y, result.offsetY)
            .putFloat(KEY_OFFSET_Z, result.offsetZ)
            .putFloat(KEY_STRENGTH, result.strength)
            .apply()
    }

    fun clear() {
        offsetX = 0f
        offsetY = 0f
        offsetZ = 0f
        expectedStrength = 0f
        preferences.edit()
            .remove(KEY_OFFSET_X)
            .remove(KEY_OFFSET_Y)
            .remove(KEY_OFFSET_Z)
            .remove(KEY_STRENGTH)
            .apply()
    }

    private companion object {
        const val PREFS_NAME = "wear_compass"
        const val KEY_OFFSET_X = "mag_offset_x"
        const val KEY_OFFSET_Y = "mag_offset_y"
        const val KEY_OFFSET_Z = "mag_offset_z"
        const val KEY_STRENGTH = "mag_strength"
    }
}

class MagCalibrationSampler {

    private val xs = ArrayList<Float>(MAX_SAMPLES)
    private val ys = ArrayList<Float>(MAX_SAMPLES)
    private val zs = ArrayList<Float>(MAX_SAMPLES)
    private val octants = IntArray(8)
    private val lastUnit = FloatArray(3)
    private var hasLastUnit = false
    private var minStrength = Float.MAX_VALUE
    private var maxStrength = 0f

    val count: Int
        get() = xs.size

    val strengthSpread: Float
        get() = if (maxStrength <= 0f) 0f else (maxStrength - minStrength) / maxStrength

    private val coveredOctants: Int
        get() = octants.count { it >= MIN_PER_OCTANT }

    val needsFlip: Boolean
        get() = count >= MIN_SAMPLES && octants.count { it > 0 } < MIN_OCTANTS

    val ready: Boolean
        get() = count >= MIN_SAMPLES && coveredOctants >= MIN_OCTANTS

    val progress: Float
        get() {
            val samplePart = (count.toFloat() / MIN_SAMPLES).coerceIn(0f, 1f)
            val octantPart = (coveredOctants.toFloat() / MIN_OCTANTS).coerceIn(0f, 1f)
            return (samplePart * 0.4f + octantPart * 0.6f).coerceIn(0f, 1f)
        }

    fun reset() {
        xs.clear()
        ys.clear()
        zs.clear()
        octants.fill(0)
        hasLastUnit = false
        minStrength = Float.MAX_VALUE
        maxStrength = 0f
    }

    fun add(x: Float, y: Float, z: Float) {
        if (count >= MAX_SAMPLES) return
        val strength = sqrt(x * x + y * y + z * z)
        if (strength <= 0f) return

        val ux = x / strength
        val uy = y / strength
        val uz = z / strength
        if (hasLastUnit) {
            val dot = ux * lastUnit[0] + uy * lastUnit[1] + uz * lastUnit[2]
            if (dot > MIN_DIRECTION_DOT) return
        }
        lastUnit[0] = ux
        lastUnit[1] = uy
        lastUnit[2] = uz
        hasLastUnit = true

        xs.add(x)
        ys.add(y)
        zs.add(z)
        val index = (if (x >= 0f) 4 else 0) + (if (y >= 0f) 2 else 0) + (if (z >= 0f) 1 else 0)
        octants[index]++
        if (strength < minStrength) minStrength = strength
        if (strength > maxStrength) maxStrength = strength
    }

    fun fit(): MagCalibrationResult? {
        if (!ready) return null
        val n = count.toDouble()
        val matrix = Array(4) { DoubleArray(4) }
        val vector = DoubleArray(4)

        for (i in 0 until count) {
            val x = xs[i].toDouble()
            val y = ys[i].toDouble()
            val z = zs[i].toDouble()
            val row = doubleArrayOf(2.0 * x, 2.0 * y, 2.0 * z, 1.0)
            val rhs = x * x + y * y + z * z
            for (r in 0..3) {
                vector[r] += row[r] * rhs
                for (c in 0..3) matrix[r][c] += row[r] * row[c]
            }
        }

        val solution = solve(matrix, vector) ?: return null
        val cx = solution[0].toFloat()
        val cy = solution[1].toFloat()
        val cz = solution[2].toFloat()
        val radiusSquared = (solution[3] + cx * cx + cy * cy + cz * cz).toFloat()
        if (radiusSquared <= 0f) return null
        val radius = sqrt(radiusSquared)

        if (!cx.isFinite() || !cy.isFinite() || !cz.isFinite() || !radius.isFinite()) return null
        if (radius < MIN_PLAUSIBLE_STRENGTH || radius > MAX_PLAUSIBLE_STRENGTH) return null

        var residualSquared = 0.0
        for (i in 0 until count) {
            val dx = xs[i] - cx
            val dy = ys[i] - cy
            val dz = zs[i] - cz
            val distance = sqrt(dx * dx + dy * dy + dz * dz) - radius
            residualSquared += distance.toDouble() * distance
        }
        val rms = sqrt(residualSquared / count)
        if (rms > radius * MAX_RESIDUAL_RATIO) return null

        val spanX = (xs.maxOrNull() ?: 0f) - (xs.minOrNull() ?: 0f)
        val spanY = (ys.maxOrNull() ?: 0f) - (ys.minOrNull() ?: 0f)
        val spanZ = (zs.maxOrNull() ?: 0f) - (zs.minOrNull() ?: 0f)
        val minimumSpan = radius * MIN_SPAN_RATIO
        if (spanX < minimumSpan || spanY < minimumSpan || spanZ < minimumSpan) return null

        return MagCalibrationResult(cx, cy, cz, radius, coveredOctants)
    }

    private fun solve(matrix: Array<DoubleArray>, vector: DoubleArray): DoubleArray? {
        val n = matrix.size
        val a = Array(n) { i -> DoubleArray(n + 1) { j -> if (j < n) matrix[i][j] else vector[i] } }

        for (col in 0 until n) {
            var pivot = col
            for (row in col + 1 until n) {
                if (abs(a[row][col]) > abs(a[pivot][col])) pivot = row
            }
            if (abs(a[pivot][col]) < 1e-9) return null
            if (pivot != col) {
                val temp = a[col]
                a[col] = a[pivot]
                a[pivot] = temp
            }
            val pivotValue = a[col][col]
            for (j in col..n) a[col][j] /= pivotValue
            for (row in 0 until n) {
                if (row == col) continue
                val factor = a[row][col]
                if (factor == 0.0) continue
                for (j in col..n) a[row][j] -= factor * a[col][j]
            }
        }

        return DoubleArray(n) { a[it][n] }
    }

    private companion object {
        const val MAX_SAMPLES = 600
        const val MIN_SAMPLES = 150
        const val MIN_OCTANTS = 6
        const val MIN_PER_OCTANT = 8
        const val MIN_DIRECTION_DOT = 0.9994f
        const val MAX_RESIDUAL_RATIO = 0.12f
        const val MIN_SPAN_RATIO = 1.2f
        const val MIN_PLAUSIBLE_STRENGTH = 15f
        const val MAX_PLAUSIBLE_STRENGTH = 200f
    }
}
