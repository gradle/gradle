/*
 * Copyright 2026 the original author or authors.
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

package gradlebuild.performance.generator.tasks

import org.gradle.api.file.DirectoryProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ProviderFactory
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
import org.gradle.work.DisableCachingByDefault

import javax.inject.Inject

/**
 * Generates one of the named JVM performance test projects by running the build-builder CLI.
 *
 * <p>These generators used to live in this repository, under
 * {@code testing/internal-performance-testing/.../org/gradle/performance/generator}. They now live in
 * <a href="https://github.com/gradle/build-builder">gradle/build-builder</a>, which also owns the
 * dependency versions baked into the generated projects.
 *
 * @see BuildBuilderGenerator for the shape-driven ({@code java}/{@code swift}/...) build-builder commands
 */
@DisableCachingByDefault(because = "Not made cacheable, yet")
abstract class BuildBuilderPerfProjectGenerator extends ProjectGeneratorTask {
    /**
     * Installation directory of the build-builder tool.
     */
    @PathSensitive(PathSensitivity.RELATIVE)
    @InputDirectory
    final DirectoryProperty buildBuilderInstall

    /**
     * Name of the performance test project to generate, as listed by
     * {@code build-builder perf-project --list}.
     */
    @Input
    String projectName

    /**
     * Directory to generate into. build-builder creates {@code <outputBaseDir>/<projectName>}.
     */
    final DirectoryProperty outputBaseDir

    /**
     * The generated project directory.
     */
    @OutputDirectory
    final DirectoryProperty generatedDir

    @Inject
    protected abstract ExecOperations getExecOperations()

    @Inject
    BuildBuilderPerfProjectGenerator(ObjectFactory objectFactory, ProviderFactory providerFactory) {
        buildBuilderInstall = objectFactory.directoryProperty()
        outputBaseDir = objectFactory.directoryProperty()
        outputBaseDir.set(project.layout.buildDirectory)
        generatedDir = objectFactory.directoryProperty()
        generatedDir.set(outputBaseDir.dir(providerFactory.provider { projectName }))
    }

    @TaskAction
    void generate() {
        def outputDir = outputBaseDir.get().asFile
        outputDir.mkdirs()
        execOperations.exec {
            it.executable = buildBuilderInstall.file("bin/build-builder").get().asFile
            it.workingDir = outputDir
            // Absolute here, but it is not a task input: the generated content is path-independent,
            // and `generatedDir` already covers what this task produces.
            it.args = ["perf-project", projectName, "--dir", outputDir.absolutePath]
        }
    }
}
