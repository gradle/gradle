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
import org.codehaus.groovy.ast.ClassNode;
import org.gradle.api.Action;
import org.gradle.groovy.scripts.ScriptSource;

public class ShortCircuitEmptyScriptCompiler implements ScriptClassCompiler {
    private final ScriptClassCompiler compiler;
    private final EmptyScriptGenerator emptyScriptGenerator;

    public ShortCircuitEmptyScriptCompiler(ScriptClassCompiler compiler, EmptyScriptGenerator emptyScriptGenerator) {
        this.compiler = compiler;
        this.emptyScriptGenerator = emptyScriptGenerator;
    }

    @Override
    public <T extends Script, M> CompiledScript<T, M> compile(ScriptSource source, ClassLoader classLoader, final MetadataExtractingTransformer<M> transformer, String classpathClosureName,
                                                        final Class<T> scriptBaseClass, Action<? super ClassNode> verifier) {
        if (source.getResource().getText().matches("\\s*")) {
            return new ClassCachingCompiledScript<T, M>(new CompiledScript<T, M>() {
                @Override
                public boolean hasImperativeStatements() {
                    return false;
                }

                public Class<? extends T> loadClass() {
                    return emptyScriptGenerator.generate(scriptBaseClass);
                }

                @Override
                public M getMetadata() {
                    return transformer.getMetadataDefaultValue();
                }
            });
        }
        return compiler.compile(source, classLoader, transformer, classpathClosureName, scriptBaseClass, verifier);
    }

}
