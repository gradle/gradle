/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.nativecode.language.cpp.tasks
import org.gradle.api.DefaultTask
import org.gradle.api.Incubating
import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.*
import org.gradle.language.jvm.internal.SimpleStaleClassCleaner
import org.gradle.nativecode.base.ToolChain
import org.gradle.nativecode.language.cpp.internal.DefaultAssembleSpec

import javax.inject.Inject
// TODO:DAZ Deal with duplication with CppCompile
/**
 * Compiles C++ source files into object files.
 */
@Incubating
class Assemble extends DefaultTask {
    private FileCollection source

    ToolChain toolChain

    @OutputDirectory
    File objectFileDir

    @InputFiles @SkipWhenEmpty // Workaround for GRADLE-2026
    FileCollection getSource() {
        source
    }

    // Invalidate output when the tool chain output changes
    @Input
    def getOutputType() {
        return toolChain.outputType
    }

    @Input
    List<String> macros

    @Input
    List<String> assemblerArgs

    @Inject
    Assemble() {
        source = project.files()
    }

    @TaskAction
    void assemble() {
        def cleaner = new SimpleStaleClassCleaner(getOutputs())
        cleaner.setDestinationDir(getObjectFileDir())
        cleaner.execute()

        def spec = new DefaultAssembleSpec()
        spec.tempDir = getTemporaryDir()

        spec.objectFileDir = getObjectFileDir()
        spec.source = getSource()
        spec.args = getAssemblerArgs()
        spec.macros = getMacros()

        def result = toolChain.createAssembler().execute(spec)
        didWork = result.didWork
    }

    void source(Object sourceFiles) {
        source.from sourceFiles
    }
}
