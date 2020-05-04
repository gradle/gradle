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

import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.Plugin
import org.gradle.api.PolymorphicDomainObjectContainer
import org.gradle.api.Project
import org.gradle.api.plugins.ExtensionAware
import org.gradle.kotlin.dsl.*
import org.gradle.plugins.ide.idea.model.IdeaModel
import org.gradle.plugins.ide.idea.model.IdeaProject
import org.jetbrains.gradle.ext.CopyrightConfiguration
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
        configureIdeaForRootProject()
    }

    private
    fun Project.configureIdeaForRootProject() {
        apply(plugin = "org.jetbrains.gradle.plugin.idea-ext")
        tasks.named("idea") {
            doFirst { throw RuntimeException("To import in IntelliJ, please follow the instructions here: https://github.com/gradle/gradle/blob/master/CONTRIBUTING.md#intellij") }
        }
        with(the<IdeaModel>()) {
            module {
                excludeDirs = setOf(intTestHomeDir)
            }
            project {
                settings {
                    configureCopyright()
                    configureRunConfigurations()
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
    fun ProjectSettings.configureRunConfigurations() {
        runConfigurations {
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
    val Project.intTestHomeDir
        get() = file("intTestHomeDir")
}


fun IdeaProject.settings(configuration: ProjectSettings.() -> Unit) = (this as ExtensionAware).configure(configuration)


fun ProjectSettings.copyright(configuration: CopyrightConfiguration.() -> Unit) = (this as ExtensionAware).configure(configuration)


fun ProjectSettings.runConfigurations(configuration: PolymorphicDomainObjectContainer<RunConfiguration>.() -> Unit) = (this as ExtensionAware).configure<NamedDomainObjectContainer<RunConfiguration>> {
    (this as PolymorphicDomainObjectContainer<RunConfiguration>).apply(configuration)
}
