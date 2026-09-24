import org.gradle.api.provider.ValueSource
import org.gradle.api.provider.ValueSourceParameters

// tag::value-source[]
abstract class EnvVarsWithSubstringValueSource : ValueSource<Map<String, String>, EnvVarsWithSubstringValueSource.Parameters> {
    interface Parameters : ValueSourceParameters {
        val substring: Property<String>
    }

    override fun obtain(): Map<String, String> {
        return System.getenv().filterKeys { key ->
            key.contains(parameters.substring.get())
        }
    }
}
// end::value-source[]

// tag::create-provider[]
val jdkLocationsProvider = providers.of(EnvVarsWithSubstringValueSource::class) {
    parameters {
        substring = "JDK"
    }
}
// end::create-provider[]
