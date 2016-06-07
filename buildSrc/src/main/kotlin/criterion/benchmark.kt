/**
 * Small criterion like benchmarking framework.
 */
package criterion

fun benchmark(config: BenchmarkConfig, block: () -> Unit): BenchmarkResult {
    warmUp(config, block)
    val observations = collectObservations(config, block)
    return BenchmarkResult(observations)
}

data class BenchmarkConfig(val warmUpRuns: Int, val observationRuns: Int)

class BenchmarkResult(observations: List<Duration>) : Result<Duration>(observations) {

    override val Duration.magnitude: Double
        get() = ns

    override val Double.measure: Duration
        get() = Duration(this)
}

private fun warmUp(config: BenchmarkConfig, block: () -> Unit) {
    for (i in 1..config.warmUpRuns) {
        block()
    }
}

private fun collectObservations(config: BenchmarkConfig, block: () -> Unit): List<Duration> =
    (1..config.observationRuns).map {
        durationOf(block)
    }
