import org.gradle.api.Incubating
import org.gradle.api.Plugin
import org.gradle.api.initialization.Settings
import org.gradle.jvm.toolchain.JavaToolchainRepository
import org.gradle.jvm.toolchain.JavaToolchainRepositoryRegistry
import org.gradle.jvm.toolchain.JavaToolchainSpec
import org.gradle.jvm.toolchain.JavaToolchainSpecVersion
import java.net.URI
import java.util.Optional
import javax.inject.Inject

apply<AzulPlugin>()
apply<AdoptiumPlugin>()

// tag::toolchain-management[]
toolchainManagement {
    jdks { // <1>
        add("com_azul_zulu") // <2>
            {
                credentials {
                    username = "user"
                    password = "password"
                }
                authentication {
                    create<DigestAuthentication>("digest")
                }
            } // <3>
        add("org_gradle_adopt_open_jdk") // <4>
    }
}
// end::toolchain-management[]

rootProject.name = "toolchain-management"

/**
 * Mock Azul repository plugin.
 *
 * @since 7.6
 */
@Incubating
abstract class AzulPlugin: DummyPlugin("com_azul_zulu")

/**
 * Mock Adoptium repository plugin.
 *
 * @since 7.6
 */
@Incubating
abstract class AdoptiumPlugin: DummyPlugin("org_gradle_adopt_open_jdk")

abstract class DummyPlugin(val repoName: String): Plugin<Settings> {

    @get:Inject
    protected abstract val toolchainRepositoryRegistry: JavaToolchainRepositoryRegistry

    override fun apply(settings: Settings) {
        settings.plugins.apply("jdk-toolchains")

        val registry: JavaToolchainRepositoryRegistry = toolchainRepositoryRegistry
        registry.register(repoName, DummyRepo::class.java, JavaToolchainSpecVersion.currentSpecVersion)
    }

}

abstract class DummyRepo: JavaToolchainRepository {
    override fun toUri(spec: JavaToolchainSpec, env: org.gradle.env.BuildEnvironment): Optional<URI> {
        return Optional.empty()
    }
}