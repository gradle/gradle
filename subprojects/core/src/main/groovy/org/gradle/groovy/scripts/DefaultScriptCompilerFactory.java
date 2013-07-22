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

import org.gradle.groovy.scripts.internal.ScriptClassCompiler;
import org.gradle.groovy.scripts.internal.ScriptRunnerFactory;
import org.gradle.internal.reflect.DirectInstantiator;
import org.gradle.internal.reflect.Instantiator;

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
        private final Instantiator instantiator = new DirectInstantiator();

        public ScriptCompilerImpl(ScriptSource source) {
            this.source = new CachingScriptSource(source);
        }

        public ScriptCompiler setClassloader(ClassLoader classloader) {
            this.classloader = classloader;
            return this;
        }

        public ScriptCompiler setTransformer(Transformer transformer) {
            this.transformer = transformer;
            return this;
        }

        public <T extends Script> ScriptRunner<T> compile(Class<T> scriptType) {
            Class<? extends T> scriptClass = scriptClassCompiler.compile(source, classloader, transformer, scriptType);
            T script = instantiator.newInstance(scriptClass);
            script.setScriptSource(source);
            script.setContextClassloader(classloader);
            return scriptRunnerFactory.create(script);
        }
    }
}
