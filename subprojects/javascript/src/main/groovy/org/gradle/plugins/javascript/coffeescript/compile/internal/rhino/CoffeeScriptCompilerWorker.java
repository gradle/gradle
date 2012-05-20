/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.plugins.javascript.coffeescript.compile.internal.rhino;

import org.apache.commons.io.FileUtils;
import org.gradle.api.UncheckedIOException;
import org.gradle.plugins.javascript.coffeescript.compile.internal.CoffeeScriptCompileDestinationCalculator;
import org.gradle.plugins.javascript.coffeescript.compile.internal.CoffeeScriptCompileTarget;
import org.gradle.plugins.javascript.coffeescript.compile.internal.SerializableCoffeeScriptCompileSpec;
import org.gradle.plugins.javascript.rhino.worker.RhinoWorker;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.RhinoException;
import org.mozilla.javascript.Scriptable;

import java.io.*;

public class CoffeeScriptCompilerWorker implements RhinoWorker<Boolean, SerializableCoffeeScriptCompileSpec> {

    public Boolean process(SerializableCoffeeScriptCompileSpec spec) {
        Scriptable coffeeScriptScope = createCoffeeScriptScope(spec.getCoffeeScriptJs());
        String encoding = spec.getOptions().getEncoding();

        CoffeeScriptCompileDestinationCalculator destinationCalculator = new CoffeeScriptCompileDestinationCalculator(spec.getDestinationDir());

        for (CoffeeScriptCompileTarget target : spec.getSource()) {
            String source = readFile(target.getFile(), encoding);
            String output = compile(coffeeScriptScope, source, target.getRelativePath().getPathString());
            writeFile(output, destinationCalculator.transform(target.getRelativePath()), encoding);
        }

        return Boolean.TRUE;
    }

    public Exception convertException(RhinoException rhinoException) {
        // TODO - need to convert this to a non rhino type in case the version is different back at the client
        return rhinoException;
    }

    private String readFile(File file, String encoding) {
        try {
            return FileUtils.readFileToString(file, encoding);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void writeFile(String content, File destination, String encoding) {
        try {
            FileUtils.writeStringToFile(destination, content, encoding);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private String compile(Scriptable rootScope, String source, String sourceName) {
        Context context = Context.enter();
        try {
            Scriptable compileScope = context.newObject(rootScope);
            compileScope.setParentScope(rootScope);
            compileScope.put("coffeeScriptSource", compileScope, source);
            return (String)context.evaluateString(compileScope, "CoffeeScript.compile(coffeeScriptSource, {});", sourceName, 0, null);
        } finally {
            Context.exit();
        }
    }

    private static Scriptable createCoffeeScriptScope(File coffeeScriptJs) {
        Context context = Context.enter();
        context.setOptimizationLevel(-1);

        Scriptable scope = context.initStandardObjects();
        try {
            // TODO we aren't considering the case where coffee-script.js is in a different encoding here
            Reader reader = new InputStreamReader(new FileInputStream(coffeeScriptJs), "UTF-8");
            try {
                context.evaluateReader(scope, reader, coffeeScriptJs.getName(), 0, null);
            } finally {
                reader.close();
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } finally {
            Context.exit();
        }

        return scope;
    }
}
