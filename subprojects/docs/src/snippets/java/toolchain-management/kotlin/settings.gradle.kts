import org.gradle.api.Incubating
import org.gradle.api.Plugin
import org.gradle.api.initialization.Settings
import org.gradle.jvm.toolchain.JavaToolchainRepository
import org.gradle.jvm.toolchain.JavaToolchainRepositoryRegistry
import org.gradle.jvm.toolchain.JavaToolchainSpec
import java.net.URI
import java.util.Optional
import javax.inject.Inject

apply<AzulPlugin>()
apply<AdoptiumPlugin>()

// tag::toolchain-management[]
toolchainManagement {
    jvm { // <1>
        resolvers {
            resolver("azul") { // <2>
                implementationClass.set(AzulRepo::class.java)
                credentials {
                    username = "user"
                    password = "password"
                }
                authentication {
                    create<DigestAuthentication>("digest")
                } // <3>
            }
            resolver("adoptium") { // <4>
                implementationClass.set(AdoptiumRepo::class.java)
            }
        }
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
abstract class AzulPlugin: DummyPlugin(AzulRepo::class)

/**
 * Mock Adoptium repository plugin.
 *
 * @since 7.6
 */
@Incubating
abstract class AdoptiumPlugin: DummyPlugin(AdoptiumRepo::class)

abstract class DummyPlugin(val repoClass: kotlin.reflect.KClass<out JavaToolchainRepository>): Plugin<Settings> {

    @get:Inject
    protected abstract val toolchainRepositoryRegistry: JavaToolchainRepositoryRegistry

    override fun apply(settings: Settings) {
        settings.plugins.apply("jvm-toolchains")

        val registry: JavaToolchainRepositoryRegistry = toolchainRepositoryRegistry
        registry.register(repoClass.java)
    }

}

abstract class AdoptiumRepo: JavaToolchainRepository {
    override fun toUri(request: JavaToolchainRequest): Optional<URI> {
        return Optional.empty()
    }
}

abstract class AzulRepo: JavaToolchainRepository {
    override fun toUri(request: JavaToolchainRequest): Optional<URI> {
        return Optional.empty()
    }
}