/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.play.tasks;

import org.gradle.api.Incubating;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.SourceTask;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.incremental.IncrementalTaskInputs;
import org.gradle.platform.base.internal.toolchain.ToolProvider;
import org.gradle.play.internal.coffeescript.CoffeeScriptCompileSpec;
import org.gradle.language.base.internal.compile.Compiler;
import org.gradle.play.internal.coffeescript.DefaultCoffeeScriptCompileSpec;
import org.gradle.play.internal.toolchain.PlayToolChainInternal;
import org.gradle.play.platform.PlayPlatform;
import org.gradle.play.toolchain.PlayToolChain;

import javax.inject.Inject;
import java.io.File;

/**
 * Task for compiling CoffeeScript sources
 */
public class CoffeeScriptCompile extends SourceTask {
    private Compiler<CoffeeScriptCompileSpec> compiler;
    private PlayPlatform platform;
    private File sourceDirectory;
    @OutputDirectory
    private File outputDirectory;

    @TaskAction
    void compile(IncrementalTaskInputs inputs) {
        CoffeeScriptCompileSpec spec = new DefaultCoffeeScriptCompileSpec(getSource().getFiles(), getSourceDirectory(), getOutputDirectory());
        getCompiler(spec).execute(spec);
    }

    private Compiler<CoffeeScriptCompileSpec> getCompiler(CoffeeScriptCompileSpec spec) {
        if (compiler == null) {
            ToolProvider select = ((PlayToolChainInternal) getToolChain()).select(platform);
            compiler = select.newCompiler(spec);
        }
        return compiler;
    }

    @Incubating
    @Inject
    public PlayToolChain getToolChain() {
        // Implementation is generated
        throw new UnsupportedOperationException();
    }

    public PlayPlatform getPlatform() {
        return platform;
    }

    public void setPlatform(PlayPlatform platform) {
        this.platform = platform;
    }

    public File getSourceDirectory() {
        return sourceDirectory;
    }

    public void setSourceDirectory(File sourceDirectory) {
        this.sourceDirectory = sourceDirectory;
        this.setSource(sourceDirectory);
    }

    public File getOutputDirectory() {
        return outputDirectory;
    }

    public void setOutputDirectory(File outputDirectory) {
        this.outputDirectory = outputDirectory;
    }

    public void setCompiler(Compiler<CoffeeScriptCompileSpec> compiler) {
        this.compiler = compiler;
    }
}
