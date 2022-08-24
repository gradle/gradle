/*
 * Copyright 2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.google.gson.stream.JsonReader
import gradlebuild.basics.repoRoot
import gradlebuild.basics.isBundleGroovy4
import gradlebuild.modules.extension.ExternalModulesExtension

val libs = extensions.create<ExternalModulesExtension>("libs", isBundleGroovy4)

applyAutomaticUpgradeOfCapabilities()
dependencies {
    components {
        // Gradle distribution - minify: remove unused transitive dependencies
        withLibraryDependencies<DependencyRemovalByNameRule>(libs.awsS3Core, setOf("jackson-dataformat-cbor"))
        withLibraryDependencies<DependencyRemovalByGroupRule>(libs.jgit, setOf("com.googlecode.javaewah"))

        // We don't need the extra annotations provided by j2objc
        withLibraryDependencies<DependencyRemovalByNameRule>(libs.googleHttpClient, setOf("j2objc-annotations"))

        // Read capabilities declared in capabilities.json
        readCapabilitiesFromJson()

        withModule<ReplaceCglibNodepWithCglibRule>("org.spockframework:spock-core")
        // Prevent Spock from pulling in Groovy and third-party dependencies - see https://github.com/spockframework/spock/issues/899
        withLibraryDependencies<DependencyRemovalByNameRule>(
            "org.spockframework:spock-core",
            setOf("groovy-groovysh", "groovy-json", "groovy-macro", "groovy-nio", "groovy-sql", "groovy-templates", "groovy-test", "groovy-xml")
        )
        withLibraryDependencies<DependencyRemovalByNameRule>("cglib:cglib", setOf("ant"))

        // asciidoctorj depends on a lot of stuff, which causes `Can't create process, argument list too long` on Windows
        withLibraryDependencies<DependencyRemovalByNameRule>("org.gradle:sample-discovery", setOf("asciidoctorj", "asciidoctorj-api"))

        withModule<DowngradeXmlApisRule>("jaxen:jaxen")
        withModule<DowngradeXmlApisRule>("jdom:jdom")
        withModule<DowngradeXmlApisRule>("xalan:xalan")
        withModule<DowngradeXmlApisRule>("jaxen:jaxen")

        // We only need "failureaccess" of Guava's dependencies
        withLibraryDependencies<KeepDependenciesByNameRule>("com.google.guava:guava", setOf("failureaccess"))

        // We only need a few utility classes of this module
        withLibraryDependencies<DependencyRemovalByNameRule>("jcifs:jcifs", setOf("servlet-api"))

        // Test dependencies - minify: remove unused transitive dependencies
        withLibraryDependencies<DependencyRemovalByNameRule>(
            "xyz.rogfam:littleproxy",
            setOf("barchart-udt-bundle", "guava", "commons-cli")
        )

        // TODO: Gradle profiler should use the bundled tooling API.
        //   This should actually be handled by conflict resolution, though it doesn't seem to work.
        //   See https://github.com/gradle/gradle/issues/12002.
        withLibraryDependencies<DependencyRemovalByNameRule>(
            "org.gradle.profiler:gradle-profiler",
            setOf("gradle-tooling-api")
        )
    }
}


fun applyAutomaticUpgradeOfCapabilities() {
    configurations.all {
        resolutionStrategy.capabilitiesResolution.all {
            selectHighestVersion()
        }
    }
}

fun readCapabilitiesFromJson() {
    val capabilitiesFile = repoRoot().file("gradle/dependency-management/capabilities.json").asFile
    val capabilities: List<CapabilitySpec> = readCapabilities(capabilitiesFile)
    capabilities.forEach {
        it.configure(dependencies.components, configurations)
    }
}

fun readCapabilities(source: File): List<CapabilitySpec> {
    JsonReader(source.reader(Charsets.UTF_8)).use { reader ->
        reader.isLenient = true
        return Gson().fromJson(reader)
    }
}

inline
fun <reified T> Gson.fromJson(json: JsonReader): T = this.fromJson(json, object : TypeToken<T>() {}.type)


abstract class CapabilityRule @Inject constructor(
    val name: String,
    val version: String
) : ComponentMetadataRule {
    override fun execute(context: ComponentMetadataContext) {
        context.details.allVariants {
            withCapabilities {
                addCapability("org.gradle.internal.capability", name, version)
            }
        }
    }
}


class CapabilitySpec {
    lateinit var name: String

    private
    lateinit var providedBy: Set<String>

    private
    lateinit var selected: String

    private
    var upgrade: String? = null

    internal
    fun configure(components: ComponentMetadataHandler, configurations: ConfigurationContainer) {
        if (upgrade != null) {
            configurations.forceUpgrade(selected, upgrade!!)
        } else {
            providedBy.forEachIndexed { idx, provider ->
                if (provider != selected) {
                    components.declareSyntheticCapability(provider, idx.toString())
                }
            }
            components.declareCapabilityPreference(selected)
        }
    }

    private
    fun ComponentMetadataHandler.declareSyntheticCapability(provider: String, version: String) {
        withModule<CapabilityRule>(provider) {
            params(name)
            params(version)
        }
    }

    private
    fun ComponentMetadataHandler.declareCapabilityPreference(module: String) {
        withModule<CapabilityRule>(module) {
            params(name)
            params("${providedBy.size + 1}")
        }
    }

    /**
     * For all modules providing a capability, always use the preferred module, even if there's no conflict.
     * In other words, will forcefully upgrade all modules providing a capability to a selected module.
     *
     * @param to the preferred module
     */
    private
    fun ConfigurationContainer.forceUpgrade(to: String, version: String) = all {
        resolutionStrategy.dependencySubstitution {
            all {
                if (providedBy.contains(requested.toString())) {
                    useTarget("$to:$version", "Forceful upgrade of capability $name")
                }
            }
        }
    }
}


abstract class DependencyRemovalByNameRule @Inject constructor(
    private val moduleToRemove: Set<String>
) : ComponentMetadataRule {
    override fun execute(context: ComponentMetadataContext) {
        context.details.allVariants {
            withDependencies {
                removeAll { moduleToRemove.contains(it.name) }
            }
        }
    }
}


abstract class DependencyRemovalByGroupRule @Inject constructor(
    private val groupsToRemove: Set<String>
) : ComponentMetadataRule {
    override fun execute(context: ComponentMetadataContext) {
        context.details.allVariants {
            withDependencies {
                removeAll { groupsToRemove.contains(it.group) }
            }
        }
    }
}


abstract class KeepDependenciesByNameRule @Inject constructor(
    private val moduleToKeep: Set<String>
) : ComponentMetadataRule {
    override fun execute(context: ComponentMetadataContext) {
        context.details.allVariants {
            withDependencies {
                removeAll { !moduleToKeep.contains(it.name) }
            }
        }
    }
}


inline
fun <reified T : ComponentMetadataRule> ComponentMetadataHandler.withLibraryDependencies(module: String, modulesToRemove: Set<String>) {
    withModule<T>(module) {
        params(modulesToRemove)
    }
}


abstract class DowngradeXmlApisRule : ComponentMetadataRule {
    override fun execute(context: ComponentMetadataContext) {
        context.details.allVariants {
            withDependencies {
                filter { it.group == "xml-apis" }.forEach {
                    it.version { require("1.4.01") }
                    it.because("Gradle has trouble with the versioning scheme and pom redirects in higher versions")
                }
            }
        }
    }
}


abstract class ReplaceCglibNodepWithCglibRule : ComponentMetadataRule {
    override fun execute(context: ComponentMetadataContext) {
        context.details.allVariants {
            withDependencies {
                filter { it.name == "cglib-nodep" }.forEach {
                    add("${it.group}:cglib:3.2.7")
                }
                removeAll { it.name == "cglib-nodep" }
            }
        }
    }
}

// https://youtrack.jetbrains.com/issue/IDEA-261387
abstract class UnifyTrove4jVersionRule : ComponentMetadataRule {
    override fun execute(context: ComponentMetadataContext) {
        context.details.allVariants {
            withDependencies {
                if (any { it.name == "trove4j" }) {
                    removeAll { it.name == "trove4j" }
                    add("org.jetbrains.intellij.deps:trove4j:trove4j")
                }
            }
        }
    }
}
