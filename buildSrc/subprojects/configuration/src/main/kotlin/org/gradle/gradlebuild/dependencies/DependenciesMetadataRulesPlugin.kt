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
package org.gradle.gradlebuild.dependencies

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.google.gson.stream.JsonReader
import library
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.ComponentMetadataContext
import org.gradle.api.artifacts.ComponentMetadataRule
import org.gradle.api.artifacts.ConfigurationContainer
import org.gradle.api.artifacts.dsl.ComponentMetadataHandler
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.extra
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import kotlin.reflect.KClass


open class DependenciesMetadataRulesPlugin : Plugin<Project> {
    companion object {
        val warnedAboutCapabilities = AtomicBoolean()
    }

    override fun apply(project: Project): Unit = project.run {
        applyAutomaticUpgradeOfCapabilities()
        dependencies {
            components {
                // Gradle distribution - minify: remove unused transitive dependencies
                withModule(library("maven3"), MavenDependencyCleaningRule::class.java)
                withLibraryDependencies(library("awsS3_core"), DependencyRemovalByNameRule::class, setOf("jackson-dataformat-cbor"))
                withLibraryDependencies(library("jgit"), DependencyRemovalByGroupRule::class, setOf("com.googlecode.javaewah"))
                withLibraryDependencies(library("maven3_wagon_http_shared"), DependencyRemovalByGroupRule::class, setOf("org.jsoup"))
                withLibraryDependencies(library("aether_connector"), DependencyRemovalByGroupRule::class, setOf("org.sonatype.sisu"))
                withLibraryDependencies(library("maven3_compat"), DependencyRemovalByGroupRule::class, setOf("org.sonatype.sisu"))
                withLibraryDependencies(library("maven3_plugin_api"), DependencyRemovalByGroupRule::class, setOf("org.sonatype.sisu"))
                withLibraryDependencies(library("maven3_settings_builder"), DependencyRemovalByGroupRule::class, setOf("org.sonatype.sisu"))

                // We don't need the extra annotations provided by j2objc
                withLibraryDependencies(library("google_http_client"), DependencyRemovalByNameRule::class, setOf("j2objc-annotations"))

                // Read capabilities declared in capabilities.json
                readCapabilitiesFromJson()

                withModule("org.spockframework:spock-core", ReplaceCglibNodepWithCglibRule::class.java)
                // Prevent Spock from pulling in Groovy and third-party dependencies - see https://github.com/spockframework/spock/issues/899
                withLibraryDependencies("org.spockframework:spock-core", DependencyRemovalByNameRule::class,
                    setOf("groovy-groovysh", "groovy-json", "groovy-macro", "groovy-nio", "groovy-sql", "groovy-templates", "groovy-test", "groovy-xml"))
                withLibraryDependencies("cglib:cglib", DependencyRemovalByNameRule::class, setOf("ant"))

                // asciidoctorj depends on a lot of stuff, which causes `Can't create process, argument list too long` on Windows
                withLibraryDependencies("org.gradle:sample-discovery", DependencyRemovalByNameRule::class, setOf("asciidoctorj", "asciidoctorj-api"))

                withModule("jaxen:jaxen", DowngradeXmlApisRule::class.java)
                withModule("jdom:jdom", DowngradeXmlApisRule::class.java)
                withModule("xalan:xalan", DowngradeXmlApisRule::class.java)
                withModule("jaxen:jaxen", DowngradeXmlApisRule::class.java)

                // We only need "failureaccess" of Guava's dependencies
                withLibraryDependencies("com.google.guava:guava", KeepDependenciesByNameRule::class, setOf("failureaccess"))

                // Test dependencies - minify: remove unused transitive dependencies
                withLibraryDependencies("org.gradle.org.littleshoot:littleproxy", DependencyRemovalByNameRule::class,
                    setOf("barchart-udt-bundle", "guava", "commons-cli"))

                // TODO: Gradle profiler should use the bundled tooling API.
                //   This should actually be handled by conflict resolution, though it doesn't seem to work.
                //   See https://github.com/gradle/gradle/issues/12002.
                withLibraryDependencies("org.gradle.profiler:gradle-profiler", DependencyRemovalByNameRule::class,
                    setOf("gradle-tooling-api"))
            }
        }
    }

    private
    fun Project.applyAutomaticUpgradeOfCapabilities() {
        configurations.all {
            resolutionStrategy.capabilitiesResolution.all {
                selectHighestVersion()
            }
        }
    }

    private
    fun Project.readCapabilitiesFromJson() {
        val extra = gradle.rootProject.extra
        val capabilities: List<CapabilitySpec>
        if (extra.has("capabilities")) {
            @Suppress("unchecked_cast")
            capabilities = extra["capabilities"] as List<CapabilitySpec>
        } else {
            val capabilitiesFile = gradle.rootProject.file("gradle/dependency-management/capabilities.json")
            if (capabilitiesFile.exists()) {
                capabilities = readCapabilities(capabilitiesFile)
            } else {
                capabilities = emptyList()
            }
            extra["capabilities"] = capabilities
        }
        capabilities.forEach {
            it.configure(dependencies.components, configurations)
        }
    }

    private
    fun readCapabilities(source: File): List<CapabilitySpec> {
        val gson = Gson()
        val reader = JsonReader(source.reader(Charsets.UTF_8))
        try {
            reader.isLenient = true
            return gson.fromJson<List<CapabilitySpec>>(reader)
        } finally {
            reader.close()
        }
    }
}


inline
fun <reified T> Gson.fromJson(json: JsonReader) = this.fromJson<T>(json, object : TypeToken<T>() {}.type)


open class CapabilityRule @Inject constructor(
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
    lateinit var providedBy: Set<String>
    lateinit var selected: String
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
        withModule(provider, CapabilityRule::class.java, {
            params(name)
            params(version)
        })
    }

    private
    fun ComponentMetadataHandler.declareCapabilityPreference(module: String) {
        withModule(module, CapabilityRule::class.java, {
            params(name)
            params("${providedBy.size + 1}")
        })
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


open class DependencyRemovalByNameRule @Inject constructor(
    val moduleToRemove: Set<String>
) : ComponentMetadataRule {
    override fun execute(context: ComponentMetadataContext) {
        context.details.allVariants {
            withDependencies {
                removeAll { moduleToRemove.contains(it.name) }
            }
        }
    }
}


open class DependencyRemovalByGroupRule @Inject constructor(
    val groupsToRemove: Set<String>
) : ComponentMetadataRule {
    override fun execute(context: ComponentMetadataContext) {
        context.details.allVariants {
            withDependencies {
                removeAll { groupsToRemove.contains(it.group) }
            }
        }
    }
}


open class KeepDependenciesByNameRule @Inject constructor(
    val moduleToKeep: Set<String>
) : ComponentMetadataRule {
    override fun execute(context: ComponentMetadataContext) {
        context.details.allVariants {
            withDependencies {
                removeAll { !moduleToKeep.contains(it.name) }
            }
        }
    }
}


open class MavenDependencyCleaningRule : ComponentMetadataRule {
    override fun execute(context: ComponentMetadataContext) {
        context.details.allVariants {
            withDependencies {
                removeAll {
                    it.name != "maven-settings-builder" &&
                        it.name != "maven-model" &&
                        it.name != "maven-model-builder" &&
                        it.name != "maven-artifact" &&
                        it.name != "maven-aether-provider" &&
                        it.group != "org.sonatype.aether"
                }
            }
        }
    }
}


fun ComponentMetadataHandler.withLibraryDependencies(module: String, kClass: KClass<out ComponentMetadataRule>, modulesToRemove: Set<String>) {
    withModule(module, kClass.java, {
        params(modulesToRemove)
    })
}


open class DowngradeXmlApisRule : ComponentMetadataRule {
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


open class ReplaceCglibNodepWithCglibRule : ComponentMetadataRule {
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


open class ExcludeDependencies : ComponentMetadataRule {
    override fun execute(context: ComponentMetadataContext) {
        context.details.allVariants {
            withDependencies {
                clear()
            }
        }
    }
}
