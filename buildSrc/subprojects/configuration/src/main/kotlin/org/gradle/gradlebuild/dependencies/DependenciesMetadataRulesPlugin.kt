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

import library
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.ConfigurationContainer
import org.gradle.api.artifacts.DirectDependenciesMetadata
import org.gradle.api.artifacts.component.ModuleComponentSelector
import org.gradle.api.artifacts.dsl.ComponentMetadataHandler
import org.gradle.kotlin.dsl.dependencies

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

                // Gradle distribution - replace similar library with different coordinates
                capability("guava")
                    .providedBy("com.google.collections:google-collections")
                    .select("com.google.guava:guava-jdk5")

                capability("junit")
                    .providedBy("junit:junit-dep")
                    .select("junit:junit")

                capability("beanshell")
                    .providedBy("org.beanshell:bsh")
                    .providedBy("org.beanshell:beanshell")
                    .select("org.apache-extras.beanshell:bsh")

                capability("commons-logging")
                    .providedBy("commons-logging:commons-logging")
                    .providedBy("commons-logging:commons-logging-api")
                    .select("org.slf4j:jcl-over-slf4j")

                capability("log4j")
                    .providedBy("log4j:log4j")
                    .select("org.slf4j:log4j-over-slf4j")

                val asmModuleSet = listOf("asm", "asm-analysis", "asm-commons", "asm-tree", "asm-util")

                asmModuleSet.forEach { asmModule ->
                    capability(asmModule)
                        .providedBy("asm:${asmModule}")
                        .providedBy("org.ow2.asm:asm-all")
                        .providedBy("org.ow2.asm:asm-debug-all")
                        .forceUpgrade("org.ow2.asm:${asmModule}")
                }

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
}

private
fun Project.capability(name: String) = CapabilityBuilder(dependencies.components, configurations, name)

private
class CapabilityBuilder(val components: ComponentMetadataHandler,
                        val configurations: ConfigurationContainer,
                        val name: String) {
    val providedBy : MutableList<String> = mutableListOf()

    fun providedBy(vararg modules: String) : CapabilityBuilder {
        providedBy.addAll(modules)
        return this
    }

    /**
     * Whenever a conflict is found, that is to say that two modules providing the same capability are
     * found in the dependency graph, prefer one module over the others. The preferred module is the
     * one passed as an argument.
     * @param module the preferred module
     */
    fun select(module: String) {
        providedBy.forEachIndexed { idx, provider ->
            if (!provider.equals(module)) {
                declareSyntheticCapability(provider, idx)
            }
        }

        declarePreference(module)
    }

    /**
     * For all modules providing a capability, always use the preferred module, even if there's no conflict.
     * In other words, will forcefully upgrade all modules providing a capability to a selected module.
     *
     * @param to the preferred module
     */
    fun forceUpgrade(to: String) {
        configurations.all {
            resolutionStrategy.dependencySubstitution {
                all {
                    if (requested is ModuleComponentSelector) {
                        val requestedModule = requested as ModuleComponentSelector
                        val module = "${requestedModule.group}:${requestedModule.module}"
                        if (providedBy.contains(module)) {
                            useTarget("${to}:${requestedModule.version}", "Forceful upgrade of capability ${name}")
                        }
                    }
                }
            }
        }
    }

    private
    fun declarePreference(module: String) {
        components.withModule(module) {
            allVariants {
                withCapabilities {
                    addCapability("org.gradle.internal.capability", name, "${providedBy.size + 1}")
                }
            }
        }
    }

    private
    fun declareSyntheticCapability(provider: String, idx: Int) {
        components.withModule(provider) {
            allVariants {
                withCapabilities {
                    val version = "${idx}"
                    addCapability("org.gradle.internal.capability", name, version)
                }
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

