plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version("0.1")
}

import org.gradle.api.Plugin
import org.gradle.api.initialization.Settings
import org.gradle.jvm.toolchain.JavaToolchainResolver
import org.gradle.jvm.toolchain.JavaToolchainResolverRegistry
import java.net.URI
import java.util.Optional
import javax.inject.Inject

apply<AdoptiumPlugin>()

// tag::toolchain-management[]
toolchainManagement {
    jvm { // <1>
        javaRepositories {
            repository("disco") { // <2>
                resolverClass.set(org.gradle.disco.DiscoToolchainResolver::class.java)
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

abstract class AdoptiumPlugin: Plugin<Settings> {

    @get:Inject
    protected abstract val toolchainResolverRegistry: JavaToolchainResolverRegistry

    override fun apply(settings: Settings) {
        settings.plugins.apply("jvm-toolchain-management")

        val registry: JavaToolchainResolverRegistry = toolchainResolverRegistry
        registry.register(AdoptiumResolver::class.java)
    }

}

abstract class AdoptiumResolver: JavaToolchainResolver {
    override fun resolve(request: JavaToolchainRequest): Optional<JavaToolchainDownload> {
        return Optional.empty()
    }
}