package criterion

import java.lang.Math.sqrt

/**
 * A result of an experiment expressed as a set of observations
 * measured in an unit [U].
 *
 * @param U the unit of measure
 */
abstract class Result<U>(observations: List<U>) {

    init {
        assert(observations.isNotEmpty())
    }

    val points = observations.sortedBy { it.magnitude }

    val median by lazy {
        val middle = points.size / 2
        if (points.size.mod(2) == 0) {
            points.subList(middle - 1, middle + 1).average()
        } else {
            points[middle]
        }
    }

    val mean by lazy {
        points.average()
    }

    val stdDev by lazy {
        sqrt(variance).measure
    }

    val variance by lazy {
        points.map { square(it.magnitude - mean.magnitude) }.average()
    }

    abstract val U.magnitude: Double

    abstract val Double.measure: U

    fun Iterable<U>.average() =
        map { it.magnitude }.average().measure
}

fun square(d: Double) = d * d
