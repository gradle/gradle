plugins {
    id("org.gradle.toolchains.foojay-resolver") version("0.3.0")
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
            repository("foojay") { // <2>
                resolverClass.set(org.gradle.toolchains.foojay.FoojayToolchainResolver::class.java)
            }
            repository("adoptium") { // <3>
                resolverClass.set(AdoptiumResolver::class.java)
                credentials {
                    username = "user"
                    password = "password"
                }
                authentication {
                    create<DigestAuthentication>("digest")
                } // <4>
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