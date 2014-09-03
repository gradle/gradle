/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.groovy.scripts.internal;

import groovy.lang.Script;
import org.codehaus.groovy.classgen.Verifier;
import org.gradle.groovy.scripts.ScriptSource;
import org.gradle.groovy.scripts.Transformer;

public class ShortCircuitEmptyScriptCompiler implements ScriptClassCompiler {
    private final ScriptClassCompiler compiler;
    private final EmptyScriptGenerator emptyScriptGenerator;

    public ShortCircuitEmptyScriptCompiler(ScriptClassCompiler compiler, EmptyScriptGenerator emptyScriptGenerator) {
        this.compiler = compiler;
        this.emptyScriptGenerator = emptyScriptGenerator;
    }

    public <T extends Script> Class<? extends T> compile(ScriptSource source, ClassLoader classLoader, Transformer transformer, Class<T> scriptBaseClass, Verifier verifier) {
        if (source.getResource().getText().matches("\\s*")) {
            return emptyScriptGenerator.generate(scriptBaseClass);
        }
        return compiler.compile(source, classLoader, transformer, scriptBaseClass, verifier);
    }
}
