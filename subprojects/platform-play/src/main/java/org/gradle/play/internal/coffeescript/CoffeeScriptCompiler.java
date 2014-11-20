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

import org.gradle.api.Transformer;
import org.gradle.api.internal.tasks.SimpleWorkResult;
import org.gradle.api.resources.ResourceException;
import org.gradle.api.tasks.WorkResult;
import org.gradle.language.base.internal.compile.Compiler;
import org.gradle.play.internal.javascript.engine.JavascriptEngine;
import org.gradle.play.internal.javascript.engine.ScriptResult;
import org.gradle.play.internal.javascript.engine.TriremeJavascriptEngine;
import org.gradle.util.CollectionUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.List;

/**
 *
 */
public class CoffeeScriptCompiler implements Compiler<CoffeeScriptCompileSpec> {
    private JavascriptEngine engine;
    private static String wrapperScript;
    public static final String WRAPPER_SCRIPT_NAME = "coffeescript-wrapper.js";

    public CoffeeScriptCompiler(JavascriptEngine engine) {
        this.engine = engine;
        if (wrapperScript == null) {
            wrapperScript = loadWrapperScript();
        }
    }

    public CoffeeScriptCompiler() {
        this(getDefaultEngine());
    }

    private static JavascriptEngine getDefaultEngine() {
        return new TriremeJavascriptEngine();
    }

    private static String loadWrapperScript() {
        ClassLoader cl = CoffeeScriptCompiler.class.getClassLoader();
        InputStream scriptStream = cl.getResourceAsStream(WRAPPER_SCRIPT_NAME);
        BufferedReader reader = new BufferedReader(new InputStreamReader(scriptStream));
        StringBuilder s = new StringBuilder();
        char[] buffer = new char[2048];
        try {
            while (reader.read(buffer, 0, buffer.length) > 0) {
                s.append(buffer);
            }
        } catch (IOException e) {
            throw new ResourceException("Failed to load coffeescript wrapper script.", e);
        }
        return s.toString();
    }

    public WorkResult execute(CoffeeScriptCompileSpec spec) {
        List<String> fileNames = CollectionUtils.collect(spec.getSources(), new Transformer<String, File>() {
            public String transform(File file) {
                return file.getPath();
            }
        });

        if (fileNames.size() == 0) {
            return new SimpleWorkResult(false);
        }

        //TODO We should feed the list of filenames into the wrapper and handle the looping in js
        // I'm guessing that will be more efficient than calling the engine over and over
        for (String fileName : fileNames) {
            String relativePath = fileName.substring(spec.getSourceDirectory().getPath().length());
            String targetPath = new File(spec.getDestinationDir(), relativePath).getPath();
            ScriptResult result = engine.execute(WRAPPER_SCRIPT_NAME, wrapperScript, new Object[] {fileName, targetPath});
            if (result.getStatus() != ScriptResult.SUCCESS) {
                throw new CoffeeScriptCompileException(String.format("Failed to compile CoffeeScript sources due to: %s", engine.getErrorMessage(result.getStatus())), result.getException());
            }
        }
        return new SimpleWorkResult(true);
    }
}
