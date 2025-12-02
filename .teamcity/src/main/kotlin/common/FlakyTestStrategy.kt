package common

enum class FlakyTestStrategy {
    INCLUDE,
    EXCLUDE,
    ONLY,
    ;

    override fun toString() = name.lowercase()
}
