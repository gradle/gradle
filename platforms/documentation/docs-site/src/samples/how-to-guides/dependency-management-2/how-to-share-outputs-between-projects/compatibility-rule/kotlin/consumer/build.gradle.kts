abstract class InstrumentedJarsRule: AttributeCompatibilityRule<LibraryElements> {
    override fun execute(details: CompatibilityCheckDetails<LibraryElements>) = details.run {
        if (consumerValue?.name == "instrumented-jar" && producerValue?.name == "jar") {
            compatible()
        }
    }
}
