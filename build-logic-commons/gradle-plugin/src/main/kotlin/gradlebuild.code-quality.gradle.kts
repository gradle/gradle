import groovy.lang.GroovySystem
import net.ltgt.gradle.errorprone.CheckSeverity
import net.ltgt.gradle.errorprone.errorprone
import org.gradle.util.internal.VersionNumber

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

plugins {
    id("base")
    id("checkstyle")
    id("codenarc")
    id("net.ltgt.errorprone")
}

open class ErrorProneProjectExtension(
    val disabledChecks: ListProperty<String>
)

open class ErrorProneSourceSetExtension(
    val enabled: Property<Boolean>
)

val errorproneExtension = project.extensions.create<ErrorProneProjectExtension>("errorprone", project.objects.listProperty<String>())
errorproneExtension.disabledChecks.addAll(
    // DISCUSS
    "EnumOrdinal", // This violation is ubiquitous, though most are benign.
    "EqualsGetClass", // Let's agree if we want to adopt Error Prone's idea of valid equals()
    "JdkObsolete", // Most of the checks are good, but we do not want to replace all LinkedLists without a good reason

    // NEVER
    "MissingSummary", // We have another mechanism to check Javadocs on public API
    "InjectOnConstructorOfAbstractClass", // We use abstract injection as a pattern
    "JavaxInjectOnAbstractMethod", // We use abstract injection as a pattern
    "JavaUtilDate", // We are fine with using Date
    "StringSplitter", // We are fine with using String.split() as is
)

project.plugins.withType<JavaBasePlugin> {
    project.extensions.getByName<SourceSetContainer>("sourceSets").configureEach {
        val extension = this.extensions.create<ErrorProneSourceSetExtension>("errorprone", project.objects.property<Boolean>())
        // Enable it only for the main source set by default, as incremental Groovy
        // joint-compilation doesn't work with the Error Prone annotation processor
        extension.enabled.convention(this.name == "main")

        project.dependencies.addProvider(
            annotationProcessorConfigurationName,
            extension.enabled.filter { it }.map { "com.google.errorprone:error_prone_core:2.29.0" }
        )

        project.tasks.named<JavaCompile>(this.compileJavaTaskName) {
            options.errorprone {
                isEnabled = extension.enabled
                checks.set(errorproneExtension.disabledChecks.map {
                    it.associateWith { CheckSeverity.OFF }
                })
            }
        }
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.errorprone {
        disableWarningsInGeneratedCode = true
        allErrorsAsWarnings = true
    }
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

val rules by configurations.creating {
    isVisible = false
    isCanBeConsumed = false

    attributes {
        attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, objects.named(LibraryElements.RESOURCES))
    }
}

val groovyVersion = GroovySystem.getVersion()
val isAtLeastGroovy4 = VersionNumber.parse(groovyVersion).major >= 4
val codenarcVersion = if (isAtLeastGroovy4) "3.1.0-groovy-4.0" else "3.1.0"

dependencies {
    rules("gradlebuild:code-quality-rules") {
        because("Provides rules defined in XML files")
    }
    codenarc("gradlebuild:code-quality-rules") {
        because("Provides the IntegrationTestFixturesRule implementation")
    }
    codenarc("org.codenarc:CodeNarc:$codenarcVersion")
    codenarc(embeddedKotlin("stdlib"))

    components {
        withModule<CodeNarcRule>("org.codenarc:CodeNarc") {
            params(groovyVersion)
        }
    }
}

fun configFile(fileName: String) = resources.text.fromFile(rules.asFileTree.filter { it.name == fileName })

checkstyle {
    toolVersion = "8.12"
    config = configFile("checkstyle.xml")
    val projectDirectory = layout.projectDirectory
    configDirectory = rules.elements.map {
        projectDirectory.dir(it.single().asFile.absolutePath).dir("checkstyle")
    }
}

plugins.withType<GroovyBasePlugin> {
    the<SourceSetContainer>().all {
        // TODO: Remove after Gradle 9: this is only used to bridge checkstyle.reportsDir.resolve() during upgrade
        @Suppress("unused")
        fun DirectoryProperty.resolve(subPath: String) = this.file(subPath)
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

abstract class CodeNarcRule @Inject constructor(
    private val groovyVersion: String
) : ComponentMetadataRule {
    override fun execute(context: ComponentMetadataContext) {
        context.details.allVariants {
            withDependencies {
                val isAtLeastGroovy4 = VersionNumber.parse(groovyVersion).major >= 4
                val groovyGroup = if (isAtLeastGroovy4) "org.apache.groovy" else "org.codehaus.groovy"
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
