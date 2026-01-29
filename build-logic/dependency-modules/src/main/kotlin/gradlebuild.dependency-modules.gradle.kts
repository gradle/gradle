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
import com.google.gson.Strictness
import com.google.gson.reflect.TypeToken
import com.google.gson.stream.JsonReader
import gradlebuild.basics.repoRoot

applyAutomaticUpgradeOfCapabilities()
dependencies {
    configurations.all {
        exclude("org.slf4j", "slf4j-simple")
    }
    components {
        // Gradle distribution - minify: remove unused transitive dependencies
        applyRule<DependencyRemovalByNameRule>("com.amazonaws:aws-java-sdk-core", setOf("jackson-dataformat-cbor"))
        applyRule<DependencyRemovalByGroupRule>("org.eclipse.jgit:org.eclipse.jgit", setOf("com.googlecode.javaewah"))

        // We don't need the extra annotations provided by j2objc
        applyRule<DependencyRemovalByNameRule>("com.google.http-client:google-http-client", setOf("j2objc-annotations"))

        // Read capabilities declared in capabilities.json
        readCapabilitiesFromJson()

        // Prevent Spock from pulling in Groovy and third-party dependencies - see https://github.com/spockframework/spock/issues/899
        applyRule<DependencyRemovalByNameRule>(
            "org.spockframework:spock-core",
            setOf("groovy-groovysh", "groovy-json", "groovy-macro", "groovy-nio", "groovy-sql", "groovy-templates", "groovy-test", "groovy-xml")
        )
        applyRule<DependencyRemovalByNameRule>("cglib:cglib", setOf("ant"))

        // We do not support running junit from Ant. Don't bundle ant-junit.
        applyRule<DependencyRemovalByNameRule>("org.apache.groovy:groovy-ant", setOf("ant-junit"))

        // GCS transitively depends on commons-logging.
        // Ensure jcl-over-slf4j is pulled in when we use GCS so it can conflict.
        applyRule<DependencyAdditionRule>("com.google.apis:google-api-services-storage", "org.slf4j:jcl-over-slf4j")

        // asciidoctorj depends on a lot of stuff, which causes `Can't create process, argument list too long` on Windows
        applyRule<DependencyRemovalByNameRule>("org.gradle:sample-discovery", setOf("asciidoctorj", "asciidoctorj-api"))

        withModule<RemoveXmlApisRule>("jaxen:jaxen")
        withModule<RemoveXmlApisRule>("jdom:jdom")
        withModule<RemoveXmlApisRule>("xalan:xalan")
        withModule<RemoveXmlApisRule>("jaxen:jaxen")

        // We only need "failureaccess" of Guava's dependencies
        applyRule<KeepDependenciesByNameRule>("com.google.guava:guava", setOf("failureaccess"))

        // We only need a few utility classes of this module
        applyRule<DependencyRemovalByNameRule>("jcifs:jcifs", setOf("servlet-api"))

        // Bsh moved coordinates. Depend on the new coordinates.
        applyRule<DependencyRemovalByGroupRule>("org.testng:testng", setOf("org.beanshell"))
        applyRule<DependencyAdditionRule>("org.testng:testng", "org.apache-extras.beanshell:bsh")

        // Test dependencies - minify: remove unused transitive dependencies
        applyRule<DependencyRemovalByNameRule>(
            "xyz.rogfam:littleproxy",
            setOf("barchart-udt-bundle", "guava", "commons-cli")
        )

        // TODO: Gradle profiler should use the bundled tooling API.
        //   This should actually be handled by conflict resolution, though it doesn't seem to work.
        //   See https://github.com/gradle/gradle/issues/12002.
        applyRule<DependencyRemovalByNameRule>(
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
        reader.strictness = Strictness.LENIENT
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
                addCapability("org.gradle.internal.capability", name, "${version}.original.${context.details.id.version}")
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
            params("${providedBy.size}")
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


abstract class DependencyAdditionRule @Inject constructor(
    private val coordinates: String
) : ComponentMetadataRule {
    override fun execute(context: ComponentMetadataContext) {
        context.details.allVariants {
            withDependencies {
                add(coordinates)
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
fun <reified T : ComponentMetadataRule> ComponentMetadataHandler.applyRule(module: String, vararg modulesToRemove: Any) {
    withModule<T>(module) {
        params(*modulesToRemove)
    }
}


/**
 * The JDK now provides these.
 */
abstract class RemoveXmlApisRule : ComponentMetadataRule {
    override fun execute(context: ComponentMetadataContext) {
        context.details.allVariants {
            withDependencies {
                removeAll { it.group == "xml-apis" }
            }
        }
    }
}
