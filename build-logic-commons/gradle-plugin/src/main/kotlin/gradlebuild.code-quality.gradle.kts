/*
 * Copyright 2022 the original author or authors.
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

import gradlebuild.nullaway.NullawayAttributes
import gradlebuild.nullaway.NullawayAttributes.addToConfiguration
import gradlebuild.nullaway.NullawayCompatibilityRule
import gradlebuild.nullaway.NullawayState
import gradlebuild.nullaway.NullawayState.DISABLED
import gradlebuild.nullaway.NullawayState.ENABLED
import gradlebuild.nullaway.NullawayStatusTask
import groovy.lang.GroovySystem
import net.ltgt.gradle.errorprone.CheckSeverity.ERROR
import net.ltgt.gradle.errorprone.CheckSeverity.OFF
import net.ltgt.gradle.errorprone.errorprone
import net.ltgt.gradle.nullaway.nullaway
import org.gradle.api.attributes.LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE
import org.gradle.api.attributes.LibraryElements.RESOURCES
import org.gradle.util.internal.VersionNumber.parse
import java.lang.System.getenv

plugins {
    id("base")
    id("checkstyle")
    id("codenarc")
    id("net.ltgt.errorprone")
    id("net.ltgt.nullaway")
}

open class ErrorProneProjectExtension(
    val nullawayEnabled: Property<Boolean>
)

open class ErrorProneSourceSetExtension(
    val enabled: Property<Boolean>
)

dependencies {
    attributesSchema {
        attribute(NullawayAttributes.nullawayAttribute) {
            compatibilityRules.add(NullawayCompatibilityRule::class.java)
        }
    }
}

val errorproneExtension = project.extensions.create<ErrorProneProjectExtension>(
    "errorprone",
    objects.property<Boolean>()
).apply {
    nullawayEnabled.convention(false)
}

project.plugins.withType<JavaBasePlugin> {
    project.extensions.getByName<SourceSetContainer>("sourceSets").configureEach {
        val isMainSourceSet = name == "main"
        if (isMainSourceSet) {
            configureMainSourceSet(this, errorproneExtension, errorproneExtension.nullawayEnabled.map { if (it) ENABLED else DISABLED })
        }
        val extension = extensions.create<ErrorProneSourceSetExtension>(
            "errorprone",
            project.objects.property<Boolean>()
        ).apply {
            // Enable it only for the main source set by default, as incremental Groovy, joint-compilation doesn't work with the Error Prone annotation processor.
            enabled.convention(isMainSourceSet)
        }
        project.tasks.named<JavaCompile>(compileJavaTaskName) {
            options.errorprone {
                isEnabled = extension.enabled
                nullaway {
                    severity = errorproneExtension.nullawayEnabled.map { if (it) ERROR else OFF }
                }
            }
        }
        addErrorProneDependency(annotationProcessorConfigurationName, extension, "com.google.errorprone:error_prone_core:2.42.0") // 🚧 sync with `distributions-dependencies/build.gradle.kts`
        addErrorProneDependency(annotationProcessorConfigurationName, extension, "com.uber.nullaway:nullaway:0.12.10")
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.errorprone {
        disable(
            "JavaxInjectOnAbstractMethod", // ☑️ We use abstract injection as a pattern.
        )
        disableAllWarnings = true // 🧀 consider this immense spam burden, remove this once to fix dedicated flaw. https://github.com/diffplug/spotless/pull/2766
        disableWarningsInGeneratedCode = true
        error( // 📋
            "MissingOverride",
            "RemoveUnusedImports",
            "SelfAssignment",
            "StringCharset",
            "StringJoin",
            "UnnecessaryLambda",
        )
        excludedPaths.set(".*groovy-dsl-plugins/output/adapter-src.*") // 🚧 disableWarningsInGeneratedCode
        if (!getenv().containsKey("CI") && getenv("chitchat").toBoolean()) {
            errorproneArgs.addAll( // 🐢 opt in auto patch.
                "-XepPatchLocation:IN_PLACE",
                "-XepPatchChecks:" +
                    "RemoveUnusedImports," +
                    "UnnecessaryLambda,"
            )
        }
        nullaway {
            // NullAway can use NullMarked instead, but for the adoption process it is more effective to assume that all gradle code is already annotated.
            // This way we can catch discrepancies in modules easier. We should make all packages NullMarked eventually too, but this is a separate task.
            annotatedPackages.add("org.gradle")
            checkContracts = true
            isJSpecifyMode = true
            severity = errorproneExtension.nullawayEnabled.map { if (it) ERROR else OFF }
        }
    }
}

val rules by configurations.creating {
    isCanBeConsumed = false

    attributes {
        attribute(LIBRARY_ELEMENTS_ATTRIBUTE, objects.named(RESOURCES))
    }
}

val codeNarcVersion = if (parse(GroovySystem.getVersion()).major >= 4) "3.6.0-groovy-4.0" else "3.6.0"

dependencies {
    rules("gradlebuild:code-quality-rules") {
        because("Provides rules defined in XML files")
    }
    codenarc("gradlebuild:code-quality-rules") {
        because("Provides the IntegrationTestFixturesRule implementation")
    }
    codenarc("org.codenarc:CodeNarc:$codeNarcVersion")
    codenarc(embeddedKotlin("stdlib"))

    components {
        withModule<CodeNarcRule>("org.codenarc:CodeNarc") {
            params(GroovySystem.getVersion())
        }
    }
}

fun configFile(fileName: String) = resources.text.fromFile(rules.asFileTree.filter { it.name == fileName })

checkstyle {
    toolVersion = "10.25.0"
    config = configFile("checkstyle.xml")
    configDirectory = rules.elements.map {
        layout.projectDirectory.dir(it.single().asFile.absolutePath).dir("checkstyle")
    }
}

plugins.withType<GroovyBasePlugin> {
    the<SourceSetContainer>().all {
        tasks.register<Checkstyle>(getTaskName("checkstyle", "groovy")) {
            config = configFile("checkstyle-groovy.xml")
            source(allGroovy)
            classpath = compileClasspath
            reports.xml.outputLocation = checkstyle.reportsDir.resolve("${this@all.name}-groovy.xml")
        }
    }
}

codenarc {
    config = configFile("codenarc.xml")
    reportFormat = "console"
}

tasks.withType<CodeNarc>().configureEach {
    if (name.contains("IntegTest")) {
        config = configFile("codenarc-integtests.xml")
    }
}

val SourceSet.allGroovy: SourceDirectorySet
    get() = the<GroovySourceDirectorySet>()

abstract class CodeNarcRule @Inject constructor(private val groovyVersion: String) : ComponentMetadataRule {
    override fun execute(context: ComponentMetadataContext) {
        context.details.allVariants {
            withDependencies {
                val groovyGroup = if (parse(groovyVersion).major >= 4) "org.apache.groovy" else "org.codehaus.groovy"
                removeAll { it.group == groovyGroup }
                add("$groovyGroup:groovy") {
                    version { prefer(groovyVersion) }
                    because("We use the packaged groovy")
                }
                add("$groovyGroup:groovy-templates") {
                    version { prefer(groovyVersion) }
                    because("We use the packaged groovy")
                }
            }
        }
    }
}

private fun configureMainSourceSet(sourceSet: SourceSet, errorproneExtension: ErrorProneProjectExtension, nullawayAttributeValue: Provider<NullawayState>) {
    // Don't care about nullaway in test fixtures or tests, they're written in Groovy anyway.
    addToConfiguration(configurations.named(sourceSet.compileClasspathConfigurationName), nullawayAttributeValue)
    project.plugins.withType<JavaLibraryPlugin> {
        // Kotlin-only projects do not hit this, so they don't have nullaway attributes on the outgoing variants.
        // Java project can in turn depend on Kotlin projects even if they have nullaway enabled.
        addToConfiguration(configurations.named(sourceSet.apiElementsConfigurationName), nullawayAttributeValue)
        addToConfiguration(configurations.named(sourceSet.runtimeElementsConfigurationName), nullawayAttributeValue)
        tasks.register<NullawayStatusTask>("nullawayStatus") {
            nullawayEnabled = errorproneExtension.nullawayEnabled
            nullawayAwareDeps = configurations.named(sourceSet.compileClasspathConfigurationName).map {
                it.incoming.artifacts
            }
        }
    }
}

@Suppress("UnstableApiUsage")
fun addErrorProneDependency(annotationProcessorConfigurationName: String, extension: ErrorProneSourceSetExtension, dep: String) {
    project.dependencies.addProvider(
        annotationProcessorConfigurationName,
        extension.enabled.filter { it }.map { dep }
    )
}

val codeQuality = tasks.register("codeQuality") {
    dependsOn(tasks.withType<CodeNarc>())
    dependsOn(tasks.withType<Checkstyle>())
    dependsOn(tasks.withType<ValidatePlugins>())
}

tasks.withType<Test>().configureEach {
    shouldRunAfter(codeQuality)
}

tasks.check {
    dependsOn(codeQuality)
}
