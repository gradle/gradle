// tag::do-this[]
class Sample {
    fun joinStrings(strings: List<String>): String {
        return strings.joinToString(separator = ", ") // <1>
    }
}
// end::do-this[]
