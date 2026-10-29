package com.swarmamr.core

/**
 * Constant-velocity Kalman filter. State: x, y, vx, vy (site frame, metres and m/s).
 *
 * - predict() runs on a clock (e.g. every 100 ms); walking is modelled as random acceleration.
 * - update() takes a position fix with its covariance and rejects outliers with a
 *   Mahalanobis gate (chi-square, 2 degrees of freedom, 99% = 9.21).
 * - When the step detector says the person is standing still, call setStationary(true):
 *   velocity is zeroed and process noise shrinks, so a standing worker does not drift.
 */
class Kalman2D(
    private val accelSigma: Double = 1.0,
    private val gate: Double = 9.21,
    private val maxRejectsInRow: Int = 3,
) {
    private val x = DoubleArray(4)
    private val p = Array(4) { DoubleArray(4) }
    var initialised = false
        private set
    private var rejectsInRow = 0
    private var stationary = false

    val position get() = Vec2(x[0], x[1])
    val velocity get() = Vec2(x[2], x[3])
    val covXX get() = p[0][0]
    val covXY get() = p[0][1]
    val covYY get() = p[1][1]
    val speed get() = kotlin.math.hypot(x[2], x[3])

    fun reset(pos: Vec2, varPos: Double) {
        x[0] = pos.x; x[1] = pos.y; x[2] = 0.0; x[3] = 0.0
        for (i in 0 until 4) for (j in 0 until 4) p[i][j] = 0.0
        p[0][0] = varPos; p[1][1] = varPos; p[2][2] = 1.0; p[3][3] = 1.0
        initialised = true
        rejectsInRow = 0
    }

    fun setStationary(still: Boolean) {
        stationary = still
        if (still) { x[2] = 0.0; x[3] = 0.0 }
    }

    fun predict(dt: Double) {
        if (!initialised || dt <= 0) return
        if (stationary) {
            x[2] = 0.0; x[3] = 0.0
        } else {
            x[0] += x[2] * dt
            x[1] += x[3] * dt
        }
        // P = F P F^T + Q with F = [[1,0,dt,0],[0,1,0,dt],[0,0,1,0],[0,0,0,1]]
        val f = arrayOf(
            doubleArrayOf(1.0, 0.0, dt, 0.0),
            doubleArrayOf(0.0, 1.0, 0.0, dt),
            doubleArrayOf(0.0, 0.0, 1.0, 0.0),
            doubleArrayOf(0.0, 0.0, 0.0, 1.0),
        )
        val fp = mul(f, p)
        val fpft = mul(fp, transpose(f))
        val q = if (stationary) accelSigma * accelSigma * 0.01 else accelSigma * accelSigma
        val dt2 = dt * dt; val dt3 = dt2 * dt / 2; val dt4 = dt2 * dt2 / 4
        fpft[0][0] += q * dt4; fpft[1][1] += q * dt4
        fpft[0][2] += q * dt3; fpft[2][0] += q * dt3
        fpft[1][3] += q * dt3; fpft[3][1] += q * dt3
        fpft[2][2] += q * dt2; fpft[3][3] += q * dt2
        copy(fpft, p)
    }

    /** Returns true if the fix was used, false if it was rejected as an outlier. */
    fun update(fix: Vec2, rXX: Double, rXY: Double, rYY: Double): Boolean {
        if (!initialised) {
            reset(fix, maxOf(rXX, rYY))
            return true
        }
        // Innovation y = z - Hx, S = H P H^T + R  (H picks x, y)
        val yx = fix.x - x[0]
        val yy = fix.y - x[1]
        val s11 = p[0][0] + rXX
        val s12 = p[0][1] + rXY
        val s22 = p[1][1] + rYY
        val det = s11 * s22 - s12 * s12
        if (det <= 0) return false
        val i11 = s22 / det; val i12 = -s12 / det; val i22 = s11 / det
        val d2 = yx * (i11 * yx + i12 * yy) + yy * (i12 * yx + i22 * yy)
        if (d2 > gate) {
            rejectsInRow++
            if (rejectsInRow >= maxRejectsInRow) {
                // The filter is the one that is wrong: start again from this fix.
                reset(fix, maxOf(rXX, rYY))
                return true
            }
            return false
        }
        rejectsInRow = 0
        // K = P H^T S^-1  (4x2)
        val k = Array(4) { r ->
            doubleArrayOf(
                p[r][0] * i11 + p[r][1] * i12,
                p[r][0] * i12 + p[r][1] * i22,
            )
        }
        for (r in 0 until 4) x[r] += k[r][0] * yx + k[r][1] * yy
        if (stationary) { x[2] = 0.0; x[3] = 0.0 }
        // P = (I - K H) P
        val newP = Array(4) { r ->
            DoubleArray(4) { c -> p[r][c] - (k[r][0] * p[0][c] + k[r][1] * p[1][c]) }
        }
        copy(newP, p)
        return true
    }

    fun update(fix: Fix) = update(fix.pos, fix.covXX, fix.covXY, fix.covYY)

    private fun mul(a: Array<DoubleArray>, b: Array<DoubleArray>) = Array(4) { r ->
        DoubleArray(4) { c -> (0 until 4).sumOf { a[r][it] * b[it][c] } }
    }

    private fun transpose(a: Array<DoubleArray>) = Array(4) { r -> DoubleArray(4) { c -> a[c][r] } }

    private fun copy(from: Array<DoubleArray>, to: Array<DoubleArray>) {
        for (r in 0 until 4) for (c in 0 until 4) to[r][c] = from[r][c]
    }
}
