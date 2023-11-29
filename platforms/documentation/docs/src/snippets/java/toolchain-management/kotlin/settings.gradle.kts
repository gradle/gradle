plugins {
    id("org.gradle.toolchains.foojay-resolver") version("0.7.0")
}

import org.gradle.api.Plugin
import org.gradle.api.initialization.Settings
import org.gradle.jvm.toolchain.JavaToolchainResolver
import org.gradle.jvm.toolchain.JavaToolchainResolverRegistry
import java.net.URI
import java.util.Optional
import javax.inject.Inject

apply<MadeUpPlugin>()

// tag::toolchain-management[]
toolchainManagement {
    jvm { // <1>
        javaRepositories {
            repository("foojay") { // <2>
                resolverClass = org.gradle.toolchains.foojay.FoojayToolchainResolver::class.java
            }
            repository("made_up") { // <3>
                resolverClass = MadeUpResolver::class.java
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

abstract class MadeUpPlugin: Plugin<Settings> {

    @get:Inject
    protected abstract val toolchainResolverRegistry: JavaToolchainResolverRegistry

    override fun apply(settings: Settings) {
        settings.plugins.apply("jvm-toolchain-management")

        val registry: JavaToolchainResolverRegistry = toolchainResolverRegistry
        registry.register(MadeUpResolver::class.java)
    }

}

abstract class MadeUpResolver: JavaToolchainResolver {
    override fun resolve(request: JavaToolchainRequest): Optional<JavaToolchainDownload> {
        return Optional.empty()
    }
}
