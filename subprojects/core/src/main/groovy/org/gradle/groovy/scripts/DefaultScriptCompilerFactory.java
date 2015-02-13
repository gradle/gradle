/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.groovy.scripts;

import org.codehaus.groovy.ast.ClassNode;
import org.gradle.api.Action;
import org.gradle.groovy.scripts.internal.*;
import org.gradle.internal.Actions;

public class DefaultScriptCompilerFactory implements ScriptCompilerFactory {
    private final ScriptRunnerFactory scriptRunnerFactory;
    private final ScriptClassCompiler scriptClassCompiler;

    public DefaultScriptCompilerFactory(ScriptClassCompiler scriptClassCompiler, ScriptRunnerFactory scriptRunnerFactory) {
        this.scriptClassCompiler = scriptClassCompiler;
        this.scriptRunnerFactory = scriptRunnerFactory;
    }

    public ScriptCompiler createCompiler(ScriptSource source) {
        return new ScriptCompilerImpl(source);
    }

    private class ScriptCompilerImpl implements ScriptCompiler {
        private final ScriptSource source;
        private ClassLoader classloader;
        private Transformer transformer;
        private Action<? super ClassNode> verifier = Actions.doNothing();
        private String classpathClosureName;

        public ScriptCompilerImpl(ScriptSource source) {
            this.source = new CachingScriptSource(source);
        }

        public ScriptCompiler setClassloader(ClassLoader classloader) {
            this.classloader = classloader;
            return this;
        }

        @Override
        public ScriptCompiler setVerifier(Action<? super ClassNode> verifier) {
            this.verifier = verifier;
            return this;
        }

        @Override
        public ScriptCompiler setClasspathClosureName(String classpathClosureName) {
            this.classpathClosureName = classpathClosureName;
            return this;
        }

        @Override
        public <T extends Script> ScriptRunner<T> compile(Class<T> scriptType, MetadataExtractingTransformer<?> extractingTransformer) {
            CompiledScript<T> scriptClass = scriptClassCompiler.compile(source, classloader, extractingTransformer, classpathClosureName, scriptType, verifier);
            return scriptRunnerFactory.create(scriptClass, source, classloader);
        }
    }
}
