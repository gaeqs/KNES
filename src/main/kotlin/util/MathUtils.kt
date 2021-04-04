package util

fun fastSin(angle: Double): Double {
    val j = angle * 0.15915
    val dec = j - j.toInt()
    return 20.785 * dec * (dec - 0.5) * (dec - 1.0)
}