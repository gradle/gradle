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
package org.gradle.gradlebuild.ide

import accessors.base
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.Plugin
import org.gradle.api.PolymorphicDomainObjectContainer
import org.gradle.api.Project
import org.gradle.api.plugins.ExtensionAware
import org.gradle.gradlebuild.PublicApi
import org.gradle.gradlebuild.docs.DecorateReleaseNotes
import org.gradle.kotlin.dsl.*
import org.gradle.plugins.ide.idea.IdeaPlugin
import org.gradle.plugins.ide.idea.model.IdeaProject
import org.jetbrains.gradle.ext.Application
import org.jetbrains.gradle.ext.CopyrightConfiguration
import org.jetbrains.gradle.ext.GroovyCompilerConfiguration
import org.jetbrains.gradle.ext.IdeaCompilerConfiguration
import org.jetbrains.gradle.ext.Inspection
import org.jetbrains.gradle.ext.JUnit
import org.jetbrains.gradle.ext.Make
import org.jetbrains.gradle.ext.ProjectSettings
import org.jetbrains.gradle.ext.Remote
import org.jetbrains.gradle.ext.RunConfiguration
import org.jetbrains.gradle.ext.TaskTriggersConfig
import java.io.File


private
const val javaCompilerHeapSpace = 3072


// Disable Java 7 and Java 8 inspections because some parts of the codebase still need to run on Java 6
private
val disabledInspections = listOf(
    // Java 7 inspections
    "Convert2Diamond", "EqualsReplaceableByObjectsCall", "SafeVarargsDetector", "TryFinallyCanBeTryWithResources", "TryWithIdenticalCatches",
    // Java 8 inspections
    "Anonymous2MethodRef", "AnonymousHasLambdaAlternative", "CodeBlock2Expr", "ComparatorCombinators", "Convert2Lambda", "Convert2MethodRef", "Convert2streamapi", "FoldExpressionIntoStream", "Guava", "Java8ArraySetAll", "Java8CollectionRemoveIf", "Java8ListSort", "Java8MapApi", "Java8MapForEach", "LambdaCanBeMethodCall", "SimplifyForEach", "StaticPseudoFunctionalStyleMethod"
)


object GradleCopyright {
    val profileName = "ASL2"
    val keyword = "Copyright"
    val notice =
        """Copyright ${"$"}{today.year} the original author or authors.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

     http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License."""
}


open class IdePlugin : Plugin<Project> {

    override fun apply(project: Project): Unit = project.run {
        configureIdeaForRootProject()
    }

    private
    fun Project.configureIdeaForRootProject() {
        apply(plugin = "org.jetbrains.gradle.plugin.idea-ext")
        allprojects {
            apply(plugin = "idea")
        }
        tasks.named("idea") {
            doFirst {
                throw RuntimeException("To import in IntelliJ, please follow the instructions here: https://github.com/gradle/gradle/blob/master/CONTRIBUTING.md#intellij")
            }
        }

        val rootProject = this
        plugins.withType<IdeaPlugin> {
            with(model) {
                module {
                    excludeDirs = excludeDirs + rootExcludeDirs
                }

                project {
                    jdkName = "11.0"
                    wildcards.add("?*.gradle")
                    vcs = "Git"

                    settings {
                        configureCompilerSettings(rootProject)
                        configureCopyright()
                        // TODO The idea-ext plugin does not yet support customizing inspections.
                        // TODO Delete .idea/inspectionProfiles and uncomment the code below when it does
                        // configureInspections()
                        configureRunConfigurations(rootProject)
                        doNotDetectFrameworks("android", "web")
                        configureSyncTasks(subprojects)
                    }
                }
            }
        }
        configureJUnitDefaults()
    }

    private
    fun ProjectSettings.configureRunConfigurations(rootProject: Project) {
        runConfigurations {
            create<Application>("Run Gradle") {
                mainClass = "org.gradle.debug.GradleRunConfiguration"
                programParameters = "help"
                workingDirectory = rootProject.projectDir.absolutePath
                moduleName = "org.gradle.integTest.integTest"
                jvmArgs = "-Dorg.gradle.daemon=false"
                beforeRun {
                    create<Make>("make") {
                        enabled = false
                    }
                }
            }
            create<Remote>("Remote debug port 5005") {
                mode = Remote.RemoteMode.ATTACH
                transport = Remote.RemoteTransport.SOCKET
                sharedMemoryAddress = "javadebug"
                host = "localhost"
                port = 5005
            }
        }
    }

    private
    fun Project.docsProject() =
        project(":docs")

    private
    val lang: String
        get() = System.getenv("LANG") ?: "en_US.UTF-8"

    private
    fun Project.configureJUnitDefaults() {
        val rootProject = this
        val docsProject = docsProject()
        docsProject.afterEvaluate {
            rootProject.plugins.withType<IdeaPlugin> {
                with(model) {
                    project {
                        settings {
                            runConfigurations {
                                create<JUnit>("defaults") {
                                    defaults = true
                                    vmParameters = getDefaultJunitVmParameters(docsProject)
                                    envs = mapOf("LANG" to lang)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private
    fun ProjectSettings.configureSyncTasks(subprojects: Set<Project>) {
        subprojects.forEach { subproject ->
            subproject.run {
                afterEvaluate {
                    taskTriggers {
                        val classpathManifest = tasks.findByName("classpathManifest")
                        if (classpathManifest != null) {
                            afterSync(classpathManifest)
                        }
                        when (subproject.name) {
                            "baseServices" -> afterSync(tasks.getByName("buildReceiptResource"))
                            "core" -> afterSync(tasks.getByName("pluginsManifest"), tasks.getByName("implementationPluginsManifest"))
                            "docs" -> afterSync(tasks.getByName("defaultImports"))
                            "internalIntegTesting" -> afterSync(tasks.getByName("prepareVersionsInfo"))
                            "kotlinCompilerEmbeddable" -> afterSync(tasks.getByName("classes"))
                            "kotlinDsl" -> afterSync(tasks["generateExtensions"])
                        }
                    }
                }
            }
        }
    }

    private
    fun ProjectSettings.configureCopyright() {
        copyright {
            useDefault = GradleCopyright.profileName
            profiles {
                create(GradleCopyright.profileName) {
                    notice = GradleCopyright.notice
                    keyword = GradleCopyright.keyword
                }
            }
        }
    }

    private
    fun ProjectSettings.configureCompilerSettings(project: Project) {
        compiler {
            processHeapSize = javaCompilerHeapSpace
            useReleaseOption = false
        }
        groovyCompiler {
            excludes {
                file("${project.rootProject.projectDir.absolutePath}/subprojects/plugins/src/test/groovy/org/gradle/api/internal/tasks/testing/junit/JUnitTestClassProcessorTest.groovy")
            }
        }
    }

    private
    fun ProjectSettings.configureInspections() {
        inspections {
            disabledInspections.forEach { name ->
                create(name) {
                    enabled = false
                }
            }
        }
    }


    private
    fun getDefaultJunitVmParameters(docsProject: Project): String {
        val rootProject = docsProject.rootProject
        val releaseNotes: DecorateReleaseNotes by docsProject.tasks
        val distsDir = rootProject.layout.buildDirectory.dir(rootProject.base.distsDirName)
        val vmParameter = mutableListOf(
            "-ea",
            // TODO: This breaks the provider
            "-Dorg.gradle.docs.releasenotes.rendered=${releaseNotes.getDestinationFile().get().getAsFile()}",
            "-DintegTest.gradleHomeDir=\$MODULE_DIR\$/build/integ test",
            "-DintegTest.gradleUserHomeDir=${rootProject.file("intTestHomeDir").absolutePath}",
            "-DintegTest.gradleGeneratedApiJarCacheDir=\$MODULE_DIR\$/build/generatedApiJars",
            "-DintegTest.libsRepo=${rootProject.file("build/repo").absolutePath}",
            "-Dorg.gradle.integtest.daemon.registry=${rootProject.file("build/daemon").absolutePath}",
            "-DintegTest.distsDir=${distsDir.get().asFile.absolutePath}",
            "-Dorg.gradle.public.api.includes=${PublicApi.includes.joinToString(":")}",
            "-Dorg.gradle.public.api.excludes=${PublicApi.excludes.joinToString(":")}",
            "-Dorg.gradle.integtest.executer=embedded",
            "-Dorg.gradle.integtest.versions=default",
            "-Dorg.gradle.integtest.testkit.compatibility=current",
            "-Xmx512m",
            "--add-opens", "java.base/java.lang=ALL-UNNAMED",
            "--add-opens", "java.base/java.lang.invoke=ALL-UNNAMED",
            "--add-opens", "java.base/java.lang.reflect=ALL-UNNAMED",
            "--add-opens", "java.base/java.util=ALL-UNNAMED",
            "--add-opens", "java.prefs/java.util.prefs=ALL-UNNAMED"
        )
        return vmParameter.joinToString(" ") {
            if (it.contains(" ") || it.contains("\$")) "\"$it\""
            else it
        }
    }
}


private
val Project.rootExcludeDirs
    get() = setOf<File>(
        file("intTestHomeDir"),
        file("buildSrc/build"),
        file("buildSrc/.gradle"))


fun IdeaProject.settings(configuration: ProjectSettings.() -> kotlin.Unit) = (this as ExtensionAware).configure(configuration)


fun ProjectSettings.taskTriggers(configuration: TaskTriggersConfig.() -> kotlin.Unit) = (this as ExtensionAware).configure(configuration)


fun ProjectSettings.compiler(configuration: IdeaCompilerConfiguration.() -> kotlin.Unit) = (this as ExtensionAware).configure(configuration)


fun ProjectSettings.groovyCompiler(configuration: GroovyCompilerConfiguration.() -> kotlin.Unit) = (this as ExtensionAware).configure(configuration)


fun ProjectSettings.copyright(configuration: CopyrightConfiguration.() -> kotlin.Unit) = (this as ExtensionAware).configure(configuration)


fun ProjectSettings.inspections(configuration: NamedDomainObjectContainer<Inspection>.() -> kotlin.Unit) = (this as ExtensionAware).configure<NamedDomainObjectContainer<Inspection>> {
    this.apply(configuration)
}


fun ProjectSettings.runConfigurations(configuration: PolymorphicDomainObjectContainer<RunConfiguration>.() -> kotlin.Unit) = (this as ExtensionAware).configure<NamedDomainObjectContainer<RunConfiguration>> {
    (this as PolymorphicDomainObjectContainer<RunConfiguration>).apply(configuration)
}
