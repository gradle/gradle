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

import gradlebuild.basics.repoRoot
import org.gradle.plugins.ide.idea.model.IdeaProject
import org.jetbrains.gradle.ext.CopyrightConfiguration
import org.jetbrains.gradle.ext.ProjectSettings
import org.jetbrains.gradle.ext.Remote
import org.jetbrains.gradle.ext.RunConfiguration

plugins {
    id("org.jetbrains.gradle.plugin.idea-ext")
}

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

tasks.idea {
    doFirst { throw RuntimeException("To import in IntelliJ, please follow the instructions here: https://github.com/gradle/gradle/blob/master/CONTRIBUTING.md#intellij") }
}

if (idea.project != null) { // may be null during script compilation
    idea {
        module {
            // We exclude some top-level directories, so their content is not indexed
            // and does not appear in search results by default
            excludeDirs = listOf(".gradle", "build", "intTestHomeDir")
                .map { repoRoot().dir(it).asFile }
                .toSet()
        }
        project {
            settings {
                configureCopyright()
                configureRunConfigurations()
                doNotDetectFrameworks("android", "web", "12wq")
            }
        }
    }
}

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


fun IdeaProject.settings(configuration: ProjectSettings.() -> Unit) = (this as ExtensionAware).configure(configuration)


fun ProjectSettings.copyright(configuration: CopyrightConfiguration.() -> Unit) = (this as ExtensionAware).configure(configuration)


fun ProjectSettings.runConfigurations(configuration: PolymorphicDomainObjectContainer<RunConfiguration>.() -> Unit) = (this as ExtensionAware).configure<NamedDomainObjectContainer<RunConfiguration>> {
    (this as PolymorphicDomainObjectContainer<RunConfiguration>).apply(configuration)
}
