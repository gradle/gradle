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
import org.gradle.plugins.ide.idea.model.IdeaModel
import org.gradle.plugins.ide.idea.model.IdeaProject
import org.jetbrains.gradle.ext.CopyrightConfiguration
import org.jetbrains.gradle.ext.JUnit
import org.jetbrains.gradle.ext.ProjectSettings
import org.jetbrains.gradle.ext.Remote
import org.jetbrains.gradle.ext.RunConfiguration


object GradleCopyright {
    const val profileName = "ASL2"
    const val keyword = "Copyright"
    const val notice =
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
        if (providers.systemProperty("idea.active").getOrElse("false").toBoolean()) {
            configureIdeaForRootProject()
        }
    }

    private
    fun Project.configureIdeaForRootProject() {
        val rootProject = this
        apply(plugin = "org.jetbrains.gradle.plugin.idea-ext")
        with(the<IdeaModel>()) {
            module {
                excludeDirs = setOf(intTestHomeDir)
            }
            project {
                settings {
                    configureCopyright()
                    configureRunConfigurations(rootProject)
                    doNotDetectFrameworks("android", "web")
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
    fun ProjectSettings.configureRunConfigurations(rootProject: Project) {
        runConfigurations {
            create<Remote>("Remote debug port 5005") {
                mode = Remote.RemoteMode.ATTACH
                transport = Remote.RemoteTransport.SOCKET
                sharedMemoryAddress = "javadebug"
                host = "localhost"
                port = 5005
            }
            create<JUnit>("defaults") {
                defaults = true
                envs = mapOf("LANG" to rootProject.lang)
                lateSetJunitVmParameters(rootProject)
            }
        }
    }

    private
    fun JUnit.lateSetJunitVmParameters(rootProject: Project) {
        // TODO This is configured late: it reads provider values because 'JUnit.vmParameters' expects a plain String
        rootProject.docsProject().afterEvaluate {
            val releaseNotes: DecorateReleaseNotes by rootProject.docsProject().tasks

            val releaseNotesFile = releaseNotes.destinationFile.get().asFile
            val gradleUserHomeDir = rootProject.intTestHomeDir.absolutePath
            val distsDir = rootProject.layout.buildDirectory.dir(rootProject.base.distsDirName).get().asFile.absolutePath
            val daemoRegistryDir = rootProject.layout.buildDirectory.dir("daemon").get().asFile.absolutePath
            val libsRepo = rootProject.layout.buildDirectory.dir("repo").get().asFile.absolutePath

            val vmParameterList = mutableListOf(
                "-ea",
                "-Dorg.gradle.docs.releasenotes.rendered=$releaseNotesFile",
                "-DintegTest.gradleHomeDir=\$MODULE_DIR\$/build/integ test",
                "-DintegTest.gradleUserHomeDir=$gradleUserHomeDir",
                "-DintegTest.gradleGeneratedApiJarCacheDir=\$MODULE_DIR\$/build/generatedApiJars",
                "-DintegTest.libsRepo=$libsRepo",
                "-Dorg.gradle.integtest.daemon.registry=$daemoRegistryDir",
                "-DintegTest.distsDir=$distsDir",
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
            vmParameters = vmParameterList.joinToString(" ") {
                if (it.contains(" ") || it.contains("\$")) "\"$it\""
                else it
            }
        }
    }

    private
    fun Project.docsProject() =
        project(":docs")

    private
    val Project.lang: String
        get() = providers.environmentVariable("LANG").getOrElse("en_US.UTF-8")

    private
    val Project.intTestHomeDir
        get() = file("intTestHomeDir")
}


fun IdeaProject.settings(configuration: ProjectSettings.() -> Unit) = (this as ExtensionAware).configure(configuration)


fun ProjectSettings.copyright(configuration: CopyrightConfiguration.() -> Unit) = (this as ExtensionAware).configure(configuration)


fun ProjectSettings.runConfigurations(configuration: PolymorphicDomainObjectContainer<RunConfiguration>.() -> Unit) = (this as ExtensionAware).configure<NamedDomainObjectContainer<RunConfiguration>> {
    (this as PolymorphicDomainObjectContainer<RunConfiguration>).apply(configuration)
}
