// tag::avoid[]
abstract class VerifySignatureTask : DefaultTask() {
    @get:InputFile
    abstract val artifact: RegularFileProperty

    @TaskAction
    fun run() {
        // BAD when the library does bytecode integrity checks on itself:
        // it sees the Gradle-modified bytecode and fails
        com.example.signing.SignatureVerifier.verify(artifact.get().asFile)
    }
}
// end::avoid[]

tasks.register<VerifySignatureTask>("verify") {
    artifact = layout.projectDirectory.file("sample.txt")
}
