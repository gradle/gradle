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
import accessors.eclipse
import accessors.idea
import org.gradle.api.Action
import org.gradle.api.JavaVersion
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.Plugin
import org.gradle.api.PolymorphicDomainObjectContainer
import org.gradle.api.Project
import org.gradle.api.plugins.ExtensionAware
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.tasks.Copy
import org.gradle.gradlebuild.ProjectGroups.projectsRequiringJava8
import org.gradle.gradlebuild.PublicApi
import org.gradle.gradlebuild.docs.PegDown
import org.gradle.kotlin.dsl.*
import org.gradle.plugins.ide.eclipse.model.AbstractClasspathEntry
import org.gradle.plugins.ide.eclipse.model.Classpath
import org.gradle.plugins.ide.eclipse.model.SourceFolder
import org.gradle.plugins.ide.idea.model.IdeaLanguageLevel
import org.gradle.plugins.ide.idea.model.IdeaModule
import org.gradle.plugins.ide.idea.model.IdeaProject
import org.jetbrains.gradle.ext.Application
import org.jetbrains.gradle.ext.CodeStyleConfig
import org.jetbrains.gradle.ext.CopyrightConfiguration
import org.jetbrains.gradle.ext.ForceBraces.FORCE_BRACES_ALWAYS
import org.jetbrains.gradle.ext.GroovyCompilerConfiguration
import org.jetbrains.gradle.ext.IdeaCompilerConfiguration
import org.jetbrains.gradle.ext.JUnit
import org.jetbrains.gradle.ext.Make
import org.jetbrains.gradle.ext.ProjectSettings
import org.jetbrains.gradle.ext.Remote
import org.jetbrains.gradle.ext.RunConfiguration
import java.io.File


const val ideConfigurationBaseName = "ideConfiguration"


fun IdeaProject.settings(configuration: ProjectSettings.() -> kotlin.Unit) = (this as ExtensionAware).configure(configuration)


fun ProjectSettings.compiler(configuration: IdeaCompilerConfiguration.() -> kotlin.Unit) = (this as ExtensionAware).configure(configuration)


fun ProjectSettings.groovyCompiler(configuration: GroovyCompilerConfiguration.() -> kotlin.Unit) = (this as ExtensionAware).configure(configuration)


fun ProjectSettings.copyright(configuration: CopyrightConfiguration.() -> kotlin.Unit) = (this as ExtensionAware).configure(configuration)


fun ProjectSettings.codeStyle(configuration: CodeStyleConfig.() -> kotlin.Unit) = (this as ExtensionAware).configure(configuration)


fun ProjectSettings.runConfigurations(configuration: PolymorphicDomainObjectContainer<RunConfiguration>.() -> kotlin.Unit) = (this as ExtensionAware).configure<NamedDomainObjectContainer<RunConfiguration>> {
    (this as PolymorphicDomainObjectContainer<RunConfiguration>).apply(configuration)
}


open class IdePlugin : Plugin<Project> {

    override fun apply(project: Project): Unit = project.run {
        configureExtensionForAllProjects()
        configureEclipseForAllProjects()
        configureIdeaForAllProjects()
        configureIdeaForRootProject()
    }

    private
    fun Project.configureExtensionForAllProjects() = allprojects {
        extensions.create<IdeExtension>(ideConfigurationBaseName, this)
    }

    private
    fun Project.configureEclipseForAllProjects() = allprojects {
        apply(plugin = "eclipse")

        plugins.withType<JavaPlugin> {
            eclipse {
                classpath {
                    file.whenMerged(Action<Classpath> {
                        //There are classes in here not designed to be compiled, but just used in our testing
                        entries.removeAll { it is AbstractClasspathEntry && it.path.contains("src/integTest/resources") }
                        //Workaround for some projects referring to themselves as dependent projects
                        entries.removeAll { it is AbstractClasspathEntry && it.path.contains("$project.name") && it.kind == "src" }
                        // Remove references to libraries in the build folder
                        entries.removeAll { it is AbstractClasspathEntry && it.path.contains("$project.name/build") && it.kind == "lib" }
                        // Remove references to other project's binaries
                        entries.removeAll { it is AbstractClasspathEntry && it.path.contains("/subprojects") && it.kind == "lib" }
                        // Add needed resources for running gradle as a non daemon java application
                        entries.add(SourceFolder("build/generated-resources/main", null))
                        if (file("build/generated-resources/test").exists()) {
                            entries.add(SourceFolder("build/generated-resources/test", null))
                        }
                    })
                }
            }
        }
    }

    private
    fun Project.configureIdeaForAllProjects() = allprojects {
        apply(plugin = "idea")
        apply(plugin = "org.jetbrains.gradle.plugin.idea-ext")
        idea {
            module {
                configureLanguageLevel(this)
            }
        }
    }

    private
    fun Project.configureIdeaForRootProject() {
        val rootProject = this
        idea {
            module {
                excludeDirs = excludeDirs + rootExcludeDirs
            }

            project {
                jdkName = "9.0"
                wildcards.add("?*.gradle")
                vcs = "Git"
                settings {
                    configureCompilerSettings(rootProject)
                    configureCopyright()
                    configureCodeStyle()
                    configureRunConfigurations(rootProject)
                    doNotDetectFrameworks("android", "web")
                }
            }
        }
        configureJUnitDefaults()
    }

    private
    fun Project.configureJUnitDefaults() {
        val rootProject = this
        val docsProject = project(":docs")
        docsProject.afterEvaluate {
            rootProject.idea {
                project {
                    settings {
                        runConfigurations {
                            create<JUnit>("defaults") {
                                defaults = true
                                val releaseNotesMarkdown: PegDown by docsProject.tasks
                                val releaseNotes: Copy by docsProject.tasks
                                val releaseNotesFileName: String = releaseNotes.property("fileName") as String
                                val defaultTestVmParams = listOf(
                                    "-ea",
                                    "-Dorg.gradle.docs.releasenotes.source=${releaseNotesMarkdown.markdownFile}",
                                    "-Dorg.gradle.docs.releasenotes.rendered=${releaseNotes.destinationDir.resolve(releaseNotesFileName)}",
                                    "-DintegTest.gradleHomeDir=\$MODULE_DIR\$/build/integ test",
                                    "-DintegTest.gradleUserHomeDir=${rootProject.file("intTestHomeDir").absolutePath}",
                                    "-DintegTest.gradleGeneratedApiJarCacheDir=\$MODULE_DIR\$/build/generatedApiJars/${rootProject.version}",
                                    "-DintegTest.libsRepo=${rootProject.file("build/repo").absolutePath}",
                                    "-Dorg.gradle.integtest.daemon.registry=${rootProject.file("build/daemon").absolutePath}",
                                    "-DintegTest.distsDir=${rootProject.base.distsDir.absolutePath}",
                                    "-Dorg.gradle.public.api.includes=${PublicApi.includes.joinToString(":")}",
                                    "-Dorg.gradle.public.api.excludes=${PublicApi.excludes.joinToString(":")}",
                                    "-Dorg.gradle.integtest.executer=embedded",
                                    "-Dorg.gradle.integtest.versions=latest",
                                    "-Dorg.gradle.integtest.native.toolChains=default",
                                    "-Dorg.gradle.integtest.multiversion=default",
                                    "-Dorg.gradle.integtest.testkit.compatibility=current",
                                    "-Xmx512m"
                                )
                                vmParameters = defaultTestVmParams.map {
                                    if (it.contains(" ")) "\"$it\"" else it
                                }.joinToString(" ")
                                val lang = System.getenv("LANG") ?: "en_US.UTF-8"
                                envs = mapOf("LANG" to lang)
                            }
                        }
                    }
                }
            }
        }
    }

    private
    fun ProjectSettings.configureRunConfigurations(rootProject: Project) {
        runConfigurations {
            val gradleRunners = mapOf(
                "Regenerate IDEA metadata" to "idea",
                "Regenerate Int Test Image" to "prepareVersionsInfo intTestImage publishLocalArchives"
            )
            gradleRunners.forEach { (name, tasks) ->
                create<Application>(name) {
                    mainClass = "org.gradle.testing.internal.util.GradlewRunner"
                    programParameters = tasks
                    workingDirectory = rootProject.projectDir.absolutePath
                    moduleName = "internalTesting"
                    envs = mapOf("TERM" to "xterm")
                    beforeRun {
                        create<Make>("make") {
                            enabled = false
                        }
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
    fun ProjectSettings.configureCodeStyle() {
        codeStyle {
            @Suppress("DEPRECATION")
            USE_SAME_INDENTS = true // deprecated!
            hardWrapAt = 200
            java {
                wrapCommentsAtRightMargin = true
                classCountToUseImportOnDemand = 999
                keepControlStatementInOneLine = false
                alignParameterDescriptions = false
                alignThrownExceptionDescriptions = false
                generatePTagOnEmptyLines = false
                keepEmptyParamTags = false
                keepEmptyThrowsTags = false
                keepEmptyReturnTags = false
                ifForceBraces = FORCE_BRACES_ALWAYS
                doWhileForceBraces = FORCE_BRACES_ALWAYS
                whileForceBraces = FORCE_BRACES_ALWAYS
                forForceBraces = FORCE_BRACES_ALWAYS
            }
            groovy {
                alignMultilineNamedArguments = false
                classCountToUseImportOnDemand = 999
                ifForceBraces = FORCE_BRACES_ALWAYS
                doWhileForceBraces = FORCE_BRACES_ALWAYS
                whileForceBraces = FORCE_BRACES_ALWAYS
                forForceBraces = FORCE_BRACES_ALWAYS
            }
        }
    }

    private
    fun ProjectSettings.configureCopyright() {
        copyright {
            useDefault = "ASL2"
            profiles {
                create("ASL2") {
                    notice =
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
                    keyword = "Copyright"
                }
            }
        }
    }

    private
    fun ProjectSettings.configureCompilerSettings(project: Project) {
        compiler {
            processHeapSize = 2048
            useReleaseOption = false
        }
        groovyCompiler {
            excludes {
                file("${project.rootProject.projectDir.absolutePath}/subprojects/plugins/src/test/groovy/org/gradle/api/internal/tasks/testing/junit/JUnitTestClassProcessorTest.groovy")
            }
        }
    }

    private
    fun Project.configureLanguageLevel(ideaModule: IdeaModule) {
        @Suppress("UNCHECKED_CAST")
        val ideaLanguageLevel =
            if (ideaModule.project in projectsRequiringJava8) "1.8"
            else "1.6"
        // Force everything to Java 6, pending detangling some int test cycles or switching to project-per-source-set mapping
        ideaModule.languageLevel = IdeaLanguageLevel(ideaLanguageLevel)
        ideaModule.targetBytecodeVersion = JavaVersion.toVersion(ideaLanguageLevel)
    }
}


private
val Project.rootExcludeDirs
    get() = setOf<File>(
        file("intTestHomeDir"),
        file("buildSrc/build"),
        file("buildSrc/.gradle"))
