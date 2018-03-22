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
import org.gradle.api.artifacts.ConfigurationContainer
import org.gradle.api.artifacts.DirectDependenciesMetadata
import org.gradle.api.artifacts.dsl.ComponentMetadataHandler
import org.gradle.kotlin.dsl.dependencies
import java.io.File

open class DependenciesMetadataRulesPlugin : Plugin<Project> {
    override fun apply(project: Project): Unit = project.run {
        dependencies {
            components {
                // Gradle distribution - minify: remove unused transitive dependencies
                withLibraryDependencies(library("maven3")) {
                    removeAll {
                        it.name != "maven-settings-builder" &&
                            it.name != "maven-model" &&
                            it.name != "maven-model-builder" &&
                            it.name != "maven-artifact" &&
                            it.name != "maven-aether-provider" &&
                            it.group != "org.sonatype.aether"
                    }
                }
                withLibraryDependencies(library("awsS3_core")) {
                    removeAll { it.name == "jackson-dataformat-cbor" }
                }
                withLibraryDependencies(library("jgit")) {
                    removeAll { it.group == "com.googlecode.javaewah" }
                }
                withLibraryDependencies(library("maven3_wagon_http_shared4")) {
                    removeAll { it.group == "org.jsoup" }
                }
                withLibraryDependencies(library("aether_connector")) {
                    removeAll { it.group == "org.sonatype.sisu" }
                }
                withLibraryDependencies(library("maven3_compat")) {
                    removeAll { it.group == "org.sonatype.sisu" }
                }
                withLibraryDependencies(library("maven3_plugin_api")) {
                    removeAll { it.group == "org.sonatype.sisu" }
                }

                // Read capabilities declared in capabilities.json
                readCapabilitiesFromJson()

                //TODO check if we can upgrade the following dependencies and remove the rules
                downgradeIvy("org.codehaus.groovy:groovy-all")
                downgradeTestNG("org.codehaus.groovy:groovy-all")

                downgradeXmlApis("jaxen:jaxen")
                downgradeXmlApis("jdom:jdom")
                downgradeXmlApis("xalan:xalan")
                downgradeXmlApis("jaxen:jaxen")

                // Test dependencies - minify: remove unused transitive dependencies
                withLibraryDependencies("org.littleshoot:littleproxy") {
                    removeAll { it.name == "barchart-udt-bundle" || it.name == "guava" || it.name == "commons-cli" }
                }
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
        reader.isLenient = true
        return gson.fromJson<List<CapabilitySpec>>(reader)
    }
}

inline
fun <reified T> Gson.fromJson(json: JsonReader) = this.fromJson<T>(json, object : TypeToken<T>() {}.type)

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
        withModule(provider) {
            allVariants {
                withCapabilities {
                    addCapability("org.gradle.internal.capability", name, version)
                }
            }
        }
    }

    private
    fun ComponentMetadataHandler.declareCapabilityPreference(module: String) {
        withModule(module) {
            allVariants {
                withCapabilities {
                    addCapability("org.gradle.internal.capability", name, "${providedBy.size + 1}")
                }
            }
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
            providedBy.forEach { source ->
                substitute(module(source))
                    .because("Forceful upgrade of capability ${name}")
                    .with(module("${to}:${version}"))
            }
        }
    }
}

fun ComponentMetadataHandler.withLibraryDependencies(module: String, action: DirectDependenciesMetadata.() -> Any) {
    withModule(module) {
        allVariants {
            withDependencies {
                action()
            }
        }
    }
}

fun ComponentMetadataHandler.downgradeIvy(module: String) {
    withModule(module) {
        allVariants {
            withDependencyConstraints {
                filter { it.group == "org.apache.ivy" }.forEach {
                    it.version { prefer("2.2.0") }
                    it.because("Gradle depends on ivy implementation details which changed with newer versions")
                }
            }
        }
    }
}

fun ComponentMetadataHandler.downgradeTestNG(module: String) {
    withModule(module) {
        allVariants {
            withDependencyConstraints {
                filter { it.group == "org.testng" }.forEach {
                    it.version { prefer("6.3.1") }
                    it.because("6.3.1 is required by Gradle and part of the distribution")
                }
            }
        }
    }
}

fun ComponentMetadataHandler.downgradeXmlApis(module: String) {
    withModule(module) {
        allVariants {
            withDependencies {
                filter { it.group == "xml-apis" }.forEach {
                    it.version { prefer("1.4.01") }
                    it.because("Gradle has trouble with the versioning scheme and pom redirects in higher versions")
                }
            }
        }
    }
}

