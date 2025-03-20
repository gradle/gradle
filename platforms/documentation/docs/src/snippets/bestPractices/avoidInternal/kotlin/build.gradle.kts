// tag::avoid-this[]
abstract class BadTask : DefaultTask() {
    @TaskAction
    fun run() {
        val majorVersion =
            org.gradle.api.internal.jvm.JavaVersionParser.parseMajorVersion( // <1>
                "21.0.0"
            )
        println("Major version $majorVersion")
    }
}

tasks.register<BadTask>("badTask")
// end::avoid-this[]

// tag::do-this[]
abstract class GoodTask : DefaultTask() {
    @TaskAction
    fun run() {
        val majorVersion =
            org.gradle.api.JavaVersion.toVersion( // <2>
                "21.0.0"
            ).majorVersion
        println("Major version $majorVersion")
    }
}

tasks.register<GoodTask>("goodTask")
// end::do-this[]
