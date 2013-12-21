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

import org.gradle.api.Action;
import org.gradle.api.internal.file.RelativeFile;
import org.gradle.plugins.javascript.coffeescript.compile.internal.CoffeeScriptCompileDestinationCalculator;
import org.gradle.plugins.javascript.coffeescript.compile.internal.SerializableCoffeeScriptCompileSpec;
import org.gradle.plugins.javascript.rhino.worker.RhinoWorker;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.RhinoException;
import org.mozilla.javascript.Scriptable;

import static org.gradle.plugins.javascript.rhino.worker.RhinoWorkerUtils.*;

public class CoffeeScriptCompilerWorker implements RhinoWorker<Boolean, SerializableCoffeeScriptCompileSpec> {

    public Boolean process(SerializableCoffeeScriptCompileSpec spec) {
        Scriptable coffeeScriptScope = parse(spec.getCoffeeScriptJs(), "UTF-8", new Action<Context>() {
            public void execute(Context context) {
                context.setOptimizationLevel(-1);
            }
        });

        String encoding = spec.getOptions().getEncoding();

        CoffeeScriptCompileDestinationCalculator destinationCalculator = new CoffeeScriptCompileDestinationCalculator(spec.getDestinationDir());

        for (RelativeFile target : spec.getSource()) {
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

    private String compile(Scriptable rootScope, final String source, final String sourceName) {
        return childScope(rootScope, new DefaultScopeOperation<String>() {
            public String action(Scriptable compileScope, Context context) {
                compileScope.put("coffeeScriptSource", compileScope, source);
                return (String)context.evaluateString(compileScope, "CoffeeScript.compile(coffeeScriptSource, {});", sourceName, 0, null);
            }
        });
    }
}
