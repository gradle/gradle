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

package org.gradle.play.internal.coffeescript;

import com.google.common.base.Charsets;
import org.gradle.api.file.FileVisitDetails;
import org.gradle.api.file.FileVisitor;
import org.gradle.api.file.RelativePath;
import org.gradle.api.internal.tasks.SimpleWorkResult;
import org.gradle.api.resources.ResourceException;
import org.gradle.api.tasks.WorkResult;
import org.gradle.language.base.internal.compile.Compiler;
import org.gradle.play.internal.javascript.engine.JavascriptEngine;
import org.gradle.play.internal.javascript.engine.ScriptResult;
import org.gradle.play.internal.javascript.engine.TriremeJavascriptEngine;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Serializable;

/**
 *
 */
public class CoffeeScriptCompiler implements Compiler<CoffeeScriptCompileSpec>, Serializable {
    private JavascriptEngine engine;
    private static String compilerScript;
    public static final String WRAPPER_SCRIPT_NAME = "coffeescript-wrapper.js";
    // TODO This should come from the org.webjars dependency, but the classloader is blocking it
    public static final String COFFEESCRIPT_MIN_NAME = "coffee-script.min.js";

    public CoffeeScriptCompiler(JavascriptEngine engine) {
        this.engine = engine;
    }

    public CoffeeScriptCompiler() {
        this(getDefaultEngine());
    }

    private static JavascriptEngine getDefaultEngine() {
        return new TriremeJavascriptEngine();
    }

    private static String getCompilerScript(ClassLoader cl) {
        if (compilerScript == null) {
            String wrapperScript = getScriptFromClasspath(cl, WRAPPER_SCRIPT_NAME);
            String coffeeScript = getScriptFromClasspath(cl, COFFEESCRIPT_MIN_NAME);
            compilerScript = coffeeScript + wrapperScript;
        }
        return compilerScript;
    }

    private static String getScriptFromClasspath(ClassLoader cl, String scriptFileName) {
        StringBuilder s = new StringBuilder();
        try {
            InputStream scriptStream = cl.getResourceAsStream(scriptFileName);
            if (scriptStream == null) {
                throw new ResourceException(String.format("%s not found in classpath", scriptFileName));
            }
            BufferedReader reader = new BufferedReader(new InputStreamReader(scriptStream, Charsets.UTF_8));

            char[] buffer = new char[2048];
            for (int i=0; i>-1; i=reader.read(buffer, 0, buffer.length)) {
                s.append(buffer, 0, i);
            }
        } catch (IOException e) {
            throw new ResourceException("Failed to load coffeescript wrapper script.", e);
        }
        return s.toString();
    }

    public WorkResult execute(final CoffeeScriptCompileSpec spec) {
        final String coffeeScriptCompiler = getCompilerScript(getClass().getClassLoader());
        if (spec.getSource().getFiles().size() == 0) {
            return new SimpleWorkResult(false);
        }

        //TODO We should feed the list of filenames into the wrapper and handle the looping in js
        // I'm guessing that will be more efficient than calling the engine over and over
        spec.getSource().getAsFileTree().visit(new FileVisitor() {
            public void visitDir(FileVisitDetails dirDetails) { }

            public void visitFile(FileVisitDetails fileDetails) {
                RelativePath relativePath = fileDetails.getRelativePath();
                File targetFile = relativePath.getFile(new File(spec.getDestinationDir(), "public"));
                String targetFileName = targetFile.getPath().replaceAll("\\.coffee$", ".js");
                ScriptResult result = engine.execute(getClass().getClassLoader(), WRAPPER_SCRIPT_NAME, coffeeScriptCompiler, new String[] {fileDetails.getFile().getPath(), targetFileName});
                if (result.getStatus() != ScriptResult.SUCCESS) {
                    throw new CoffeeScriptCompileException(String.format("Failed to compile CoffeeScript sources due to: %s", engine.getErrorMessage(result.getStatus())), result.getException());
                }
            }
        });

        return new SimpleWorkResult(true);
    }
}
