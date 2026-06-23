import org.gradle.api.provider.ValueSource
import org.gradle.api.provider.ValueSourceParameters
import java.io.ByteArrayOutputStream
import java.nio.charset.Charset

// tag::value-source[]
abstract class GitVersionValueSource : ValueSource<String, ValueSourceParameters.None> {
    @get:Inject
    abstract val execOperations: ExecOperations

    override fun obtain(): String {
        val output = ByteArrayOutputStream()
        execOperations.exec {
            commandLine("git", "--version")
            standardOutput = output
        }
        return String(output.toByteArray(), Charset.defaultCharset())
    }
}
// end::value-source[]

// tag::create-provider[]
val gitVersionProvider = providers.of(GitVersionValueSource::class) {}
val gitVersion = gitVersionProvider.get()
// end::create-provider[]
