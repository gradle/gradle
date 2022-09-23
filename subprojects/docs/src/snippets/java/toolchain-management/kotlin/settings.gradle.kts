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
        javaRepositories {
            repository("azul") { // <2>
                resolverClass.set(AzulResolver::class.java)
                credentials {
                    username = "user"
                    password = "password"
                }
                authentication {
                    create<DigestAuthentication>("digest")
                } // <3>
            }
            repository("adoptium") { // <4>
                resolverClass.set(AdoptiumResolver::class.java)
            }
        }
    }
}
// end::toolchain-management[]

rootProject.name = "toolchain-management"

abstract class AzulPlugin: DummyPlugin(AzulResolver::class)

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
    override fun resolve(request: JavaToolchainRequest): Optional<JavaToolchainDownload> {
        return Optional.empty()
    }
}

abstract class AzulResolver: JavaToolchainResolver {
    override fun resolve(request: JavaToolchainRequest): Optional<JavaToolchainDownload> {
        return Optional.empty()
    }
}