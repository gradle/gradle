/*
 * Copyright 2020 the original author or authors.
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

package gradlebuild.run.tasks

import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.internal.ProcessOperations
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.options.Option
// Using star import to workaround https://youtrack.jetbrains.com/issue/KTIJ-24390
import org.gradle.kotlin.dsl.*
import org.gradle.work.DisableCachingByDefault
import java.io.File


@DisableCachingByDefault(because = "Not worth caching")
abstract class RunEmbeddedGradle : DefaultTask() {

    @get:Classpath
    abstract val gradleClasspath: ConfigurableFileCollection

    @Option(option = "args", description = "Arguments for invoking Gradle, separated by spaces.")
    fun setAllArgs(args: String) {
        this.args = args.split(' ').filter { it.isNotEmpty() }
    }

    @get:Input
    @set:Option(option = "arg", description = "Argument for invoking Gradle, can be specified multiple times.")
    var args: List<String> = listOf()

    @get:Input
    @get:Option(option = "working-dir", description = "Working dir for invoking Gradle")
    abstract val workingDir: Property<String>

    @get:javax.inject.Inject
    abstract val processOperations: ProcessOperations

    @TaskAction
    fun runGradle() {
        processOperations.javaexec {
            classpath(gradleClasspath)
            mainClass = "org.gradle.launcher.Main"
            workingDir = File(this@RunEmbeddedGradle.workingDir.get())
            jvmArgs?.add("-Dorg.gradle.daemon=false")
            args = this@RunEmbeddedGradle.args
        }
    }
}
