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
package org.gradle.groovy.scripts.internal;

import org.gradle.api.GradleScriptException;
import org.gradle.groovy.scripts.Script;
import org.gradle.groovy.scripts.ScriptExecutionListener;
import org.gradle.groovy.scripts.ScriptRunner;
import org.gradle.groovy.scripts.ScriptSource;
import org.gradle.internal.reflect.Instantiator;

public class DefaultScriptRunnerFactory implements ScriptRunnerFactory {
    private final ScriptExecutionListener listener;
    private final Instantiator instantiator;

    public DefaultScriptRunnerFactory(ScriptExecutionListener listener, Instantiator instantiator) {
        this.listener = listener;
        this.instantiator = instantiator;
    }

    public <T extends Script, M> ScriptRunner<T, M> create(CompiledScript<T, M> script, ScriptSource source, ClassLoader contextClassLoader) {
        return new ScriptRunnerImpl<T, M>(script, source, contextClassLoader);
    }

    private class ScriptRunnerImpl<T extends Script, M> implements ScriptRunner<T, M> {
        private final ScriptSource source;
        private final ClassLoader contextClassLoader;
        private T script;
        private final CompiledScript<T, M> compiledScript;

        public ScriptRunnerImpl(CompiledScript<T, M> compiledScript, ScriptSource source, ClassLoader contextClassLoader) {
            this.compiledScript = compiledScript;
            this.source = source;
            this.contextClassLoader= contextClassLoader;
        }

        @Override
        public T getScript() {
            if (script == null) {
                script = instantiator.newInstance(compiledScript.loadClass());
                script.setScriptSource(source);
                script.setContextClassloader(contextClassLoader);
            }
            return script;
        }

        @Override
        public CompiledScript<T, M> getCompiledScript() {
            return compiledScript;
        }

        @Override
        public void run() throws GradleScriptException {
            ClassLoader originalLoader = Thread.currentThread().getContextClassLoader();
            T script = getScript();
            listener.beforeScript(script);
            GradleScriptException failure = null;
            Thread.currentThread().setContextClassLoader(script.getContextClassloader());
            script.getStandardOutputCapture().start();
            try {
                script.run();
            } catch (Throwable e) {
                failure = new GradleScriptException(String.format("A problem occurred evaluating %s.", script), e);
            }
            script.getStandardOutputCapture().stop();
            Thread.currentThread().setContextClassLoader(originalLoader);
            listener.afterScript(script, failure);
            if (failure != null) {
                throw failure;
            }
        }
    }
}
