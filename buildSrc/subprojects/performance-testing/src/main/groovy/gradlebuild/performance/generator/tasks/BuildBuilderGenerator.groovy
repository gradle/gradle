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

package gradlebuild.performance.generator.tasks

import org.gradle.api.Action
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.ProviderFactory
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecSpec

import javax.inject.Inject
import java.util.concurrent.Callable
/**
 * Uses the build-builder to generate Gradle projects of different kinds.
 *
 * @see <a href="https://github.com/adammurdoch/build-builder">build builder</a>
 */
class BuildBuilderGenerator extends ProjectGeneratorTask {
    /**
     * Installation directory of the build-builder tool.
     */
    @PathSensitive(PathSensitivity.RELATIVE)
    @InputDirectory
    final DirectoryProperty buildBuilderInstall

    /**
     * Output directory for the generated project
     */
    @OutputDirectory
    final DirectoryProperty generatedDir

    /**
     * Additional arguments to provide to the build-builder.
     *
     * NOTE: Prefer to model something more concretely here vs adding extra arguments.
     */
    @Input
    final ListProperty<String> args

    /**
     * Type of project to generate (cpp, java, swift, etc)
     */
    @Input
    String projectType

    /**
     * Number of projects to generate
     */
    @Input
    int projects

    /**
     * Number of source files to generate per project
     */
    @Input
    int sourceFiles

    @Inject
    BuildBuilderGenerator(ObjectFactory objectFactory, ProviderFactory providerFactory) {
        buildBuilderInstall = objectFactory.directoryProperty()
        generatedDir = objectFactory.directoryProperty()
        generatedDir.set(project.layout.buildDirectory.dir(getName()))
        args = objectFactory.listProperty(String)
        args.set(providerFactory.provider(new Callable<Iterable<String>>() {
            @Override
            Iterable<String> call() throws Exception {
                // TODO: Model the other build builder options
                return [
                    projectType,
                    "--projects", Integer.toString(projects),
                    "--source-files", Integer.toString(sourceFiles),
                    "--dir", generatedDir.get().asFile.absolutePath
                ]
            }
        }))
    }

    @TaskAction
    void generate() {
        // TODO: Use the worker API
        def resolvedArgs = args.get()
        project.exec(new Action<ExecSpec>() {
            @Override
            void execute(ExecSpec execSpec) {
                execSpec.executable = buildBuilderInstall.file("bin/build-builder").get().asFile
                execSpec.workingDir = generatedDir.get().asFile
                execSpec.args = resolvedArgs
            }
        })
    }
}
