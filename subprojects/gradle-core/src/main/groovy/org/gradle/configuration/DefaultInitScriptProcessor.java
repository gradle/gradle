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

import org.gradle.api.internal.GradleInternal;
import org.gradle.api.internal.project.ImportsReader;
import org.gradle.api.internal.artifacts.dsl.InitScriptClasspathScriptTransformer;
import org.gradle.api.internal.artifacts.dsl.InitScriptTransformer;
import org.gradle.groovy.scripts.*;
import org.gradle.initialization.InitScript;

/**
 * Processes (and runs) an init script for a specified build.  Handles defining
 * the classpath based on the initscript {} configuration closure.
 */
public class DefaultInitScriptProcessor implements InitScriptProcessor {
    private final ScriptCompilerFactory scriptCompilerFactory;
    private final ImportsReader importsReader;

    public DefaultInitScriptProcessor(ScriptCompilerFactory scriptCompilerFactory, ImportsReader importsReader) {
        this.scriptCompilerFactory = scriptCompilerFactory;
        this.importsReader = importsReader;
    }

    public void process(ScriptSource initScript, GradleInternal gradle) {
        ScriptSource withImports = new ImportsScriptSource(initScript, importsReader, null);
        ScriptCompiler compiler = scriptCompilerFactory.createCompiler(withImports);
        compiler.setClassloader(gradle.getClassLoaderProvider().getClassLoader());

        compiler.setTransformer(new InitScriptClasspathScriptTransformer());
        ScriptRunner classPathScript = compiler.compile(InitScript.class);
        classPathScript.setDelegate(gradle);

        classPathScript.run();
        gradle.getClassLoaderProvider().updateClassPath();

        compiler.setTransformer(new InitScriptTransformer());
        ScriptRunner script = compiler.compile(InitScript.class);
        script.setDelegate(gradle);

        script.run();
    }
}