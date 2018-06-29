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
import java.io.File
import javax.inject.Inject
import kotlin.reflect.KClass


open class DependenciesMetadataRulesPlugin : Plugin<Project> {
    override fun apply(project: Project): Unit = project.run {
        dependencies {
            components {
                // Gradle distribution - minify: remove unused transitive dependencies
                withModule(library("maven3"), MavenDependencyCleaningRule::class.java)
                withLibraryDependencies(library("awsS3_core"), DependencyRemovalByNameRule::class, setOf("jackson-dataformat-cbor"))
                withLibraryDependencies(library("jgit"), DependencyRemovalByGroupRule::class, setOf("com.googlecode.javaewah"))
                withLibraryDependencies(library("maven3_wagon_http_shared4"), DependencyRemovalByGroupRule::class, setOf("org.jsoup"))
                withLibraryDependencies(library("aether_connector"), DependencyRemovalByGroupRule::class, setOf("org.sonatype.sisu"))
                withLibraryDependencies(library("maven3_compat"), DependencyRemovalByGroupRule::class, setOf("org.sonatype.sisu"))
                withLibraryDependencies(library("maven3_plugin_api"), DependencyRemovalByGroupRule::class, setOf("org.sonatype.sisu"))

                // Read capabilities declared in capabilities.json
                readCapabilitiesFromJson()

                withModule("org.spockframework:spock-core", ReplaceCglibNodepWithCglibRule::class.java)
                withModule("org.jmock:jmock-legacy", ReplaceCglibNodepWithCglibRule::class.java)

                //TODO check if we can upgrade the following dependencies and remove the rules
                withModule("org.codehaus.groovy:groovy-all", DowngradeIvyRule::class.java)
                withModule("org.codehaus.groovy:groovy-all", DowngradeTestNGRule::class.java)

                withModule("jaxen:jaxen", DowngradeXmlApisRule::class.java)
                withModule("jdom:jdom", DowngradeXmlApisRule::class.java)
                withModule("xalan:xalan", DowngradeXmlApisRule::class.java)
                withModule("jaxen:jaxen", DowngradeXmlApisRule::class.java)

                // Test dependencies - minify: remove unused transitive dependencies
                withLibraryDependencies("org.littleshoot:littleproxy", DependencyRemovalByNameRule::class,
                    setOf("barchart-udt-bundle", "guava", "commons-cli"))
            }
        }
    }

    private
    fun Project.readCapabilitiesFromJson() {
        val capabilitiesFile = gradle.rootProject.file("gradle/dependency-management/capabilities.json")
        if (capabilitiesFile.exists()) {
            readCapabilities(capabilitiesFile).forEach {
                it.configure(dependencies.components, configurations)
            }
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
    lateinit var providedBy: List<String>
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
            providedBy.forEach { source ->
                substitute(module(source))
                    .because("Forceful upgrade of capability $name")
                    .with(module("$to:$version"))
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


open class DowngradeIvyRule : ComponentMetadataRule {
    override fun execute(context: ComponentMetadataContext) {
        context.details.allVariants {
            withDependencyConstraints {
                filter { it.group == "org.apache.ivy" }.forEach {
                    it.version { prefer("2.2.0") }
                    it.because("Gradle depends on ivy implementation details which changed with newer versions")
                }
            }
        }
    }
}


open class DowngradeTestNGRule : ComponentMetadataRule {
    override fun execute(context: ComponentMetadataContext) {
        context.details.allVariants {
            withDependencyConstraints {
                filter { it.group == "org.testng" }.forEach {
                    it.version { prefer("6.3.1") }
                    it.because("6.3.1 is required by Gradle and part of the distribution")
                }
            }
        }
    }
}


open class DowngradeXmlApisRule : ComponentMetadataRule {
    override fun execute(context: ComponentMetadataContext) {
        context.details.allVariants {
            withDependencies {
                filter { it.group == "xml-apis" }.forEach {
                    it.version { prefer("1.4.01") }
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
                    add("${it.group}:cglib:3.2.6")
                }
                removeAll { it.name == "cglib-nodep" }
            }
        }
    }
}
