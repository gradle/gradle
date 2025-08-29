/*
 * Copyright 2018 the original author or authors.
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

package gradlebuild.integrationtests.model

import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileCollection
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity

abstract class GradleDistribution {

    @get:Internal
    abstract val homeDir: DirectoryProperty

    @get:Input
    val name: Provider<String> = homeDir.asFile.map { it.parentFile.parentFile.name }

    /**
     * Make sure this stays type FileCollection (lazy) to avoid losing dependency information.
     */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    val staticContent: FileCollection
        get() = homeDir.asFileTree.matching {
            exclude("lib/**")
            exclude("src/**")
            exclude("docs/**")
            exclude("README")
            exclude("getting-started.html")
        }

    @get:Classpath
    val coreJars: FileCollection
        get() = homeDir.asFileTree.matching {
            include("lib/*.jar")
        }

    @get:Classpath
    val agentJars: FileCollection
        get() = homeDir.asFileTree.matching {
            include("lib/agents/*.jar")
        }

    @get:Classpath
    val pluginJars: FileCollection
        get() = homeDir.asFileTree.matching {
            include("lib/plugins/*.jar")
        }

}
