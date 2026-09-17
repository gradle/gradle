// A task type we don't control (imagine it comes from a third-party plugin).
abstract class SomeThirdPartyTask : DefaultTask() {
    @TaskAction
    fun run() = Unit
}

tasks.register<SomeThirdPartyTask>("someThirdPartyTask")

// tag::thirdParty[]
interface StampExtension { // <1>
    val message: Property<String>
}

tasks.named("someThirdPartyTask") {
    val stamp = extensions.create<StampExtension>("stamp") // <2>
    stamp.message.convention("built by team X")

    val messageProvider = stamp.message // <3>
    doLast {
        println(messageProvider.get()) // <4>
    }
}
// end::thirdParty[]
