import org.gradle.api.Incubating
import org.gradle.api.Plugin
import org.gradle.api.initialization.Settings
import org.gradle.jvm.toolchain.JavaToolchainResolver
import org.gradle.jvm.toolchain.JavaToolchainResolverRegistry
import java.net.URI
import java.util.Optional
import javax.inject.Inject

apply<AzulPlugin>()
apply<AdoptiumPlugin>()

// tag::toolchain-management[]
toolchainManagement {
    jvm { // <1>
        repositories {
            repository("azul") { // <2>
                implementationClass.set(AzulResolver::class.java)
                credentials {
                    username = "user"
                    password = "password"
                }
                authentication {
                    create<DigestAuthentication>("digest")
                } // <3>
            }
            repository("adoptium") { // <4>
                implementationClass.set(AdoptiumResolver::class.java)
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
abstract class AzulPlugin: DummyPlugin(AzulResolver::class)

/**
 * Mock Adoptium repository plugin.
 *
 * @since 7.6
 */
@Incubating
abstract class AdoptiumPlugin: DummyPlugin(AdoptiumResolver::class)

abstract class DummyPlugin(val resolverClass: kotlin.reflect.KClass<out JavaToolchainResolver>): Plugin<Settings> {

    @get:Inject
    protected abstract val toolchainResolverRegistry: JavaToolchainResolverRegistry

    override fun apply(settings: Settings) {
        settings.plugins.apply("jvm-toolchain-management")

        val registry: JavaToolchainResolverRegistry = toolchainResolverRegistry
        registry.register(resolverClass.java)
    }

}

abstract class AdoptiumResolver: JavaToolchainResolver {
    override fun toUri(request: JavaToolchainRequest): Optional<URI> {
        return Optional.empty()
    }
}

abstract class AzulResolver: JavaToolchainResolver {
    override fun toUri(request: JavaToolchainRequest): Optional<URI> {
        return Optional.empty()
    }
}