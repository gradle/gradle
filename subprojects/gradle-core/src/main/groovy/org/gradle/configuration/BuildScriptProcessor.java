/*
 * Copyright 2009 the original author or authors.
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
package org.gradle.configuration;

import org.gradle.api.internal.artifacts.dsl.BuildScriptClasspathScriptTransformer;
import org.gradle.api.internal.artifacts.dsl.BuildScriptTransformer;
import org.gradle.api.internal.project.ImportsReader;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.project.ProjectScript;
import org.gradle.groovy.scripts.*;

public class BuildScriptProcessor implements ProjectEvaluator {
    private final ImportsReader importsReader;
    private final ScriptCompilerFactory scriptCompilerFactory;

    public BuildScriptProcessor(ImportsReader importsReader, ScriptCompilerFactory scriptCompilerFactory) {
        this.importsReader = importsReader;
        this.scriptCompilerFactory = scriptCompilerFactory;
    }

    public void evaluate(ProjectInternal project) {
        ScriptSource source = new ImportsScriptSource(project.getBuildScriptSource(), importsReader,
                project.getRootDir());

        ScriptCompiler compiler = scriptCompilerFactory.createCompiler(source);
        compiler.setClassloader(project.getClassLoaderProvider().getClassLoader());

        compiler.setTransformer(new BuildScriptClasspathScriptTransformer());
        ScriptRunner classPathScript = compiler.compile(ProjectScript.class);
        classPathScript.setDelegate(project);
        classPathScript.run();
        project.getClassLoaderProvider().updateClassPath();

        compiler.setTransformer(new BuildScriptTransformer());
        ScriptRunner script = compiler.compile(ProjectScript.class);
        script.setDelegate(project);
        project.setScript(script.getScript());
        script.run();
    }
}
