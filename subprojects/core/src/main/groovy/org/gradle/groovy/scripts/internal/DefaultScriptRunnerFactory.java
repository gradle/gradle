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

public class DefaultScriptRunnerFactory implements ScriptRunnerFactory {
    private final ScriptExecutionListener listener;

    public DefaultScriptRunnerFactory(ScriptExecutionListener listener) {
        this.listener = listener;
    }

    public <T extends Script> ScriptRunner<T> create(T script) {
        return new ScriptRunnerImpl<T>(script);
    }

    private class ScriptRunnerImpl<T extends Script> implements ScriptRunner<T> {
        private final T script;

        public ScriptRunnerImpl(T script) {
            this.script = script;
        }

        public T getScript() {
            return script;
        }

        public void run() throws GradleScriptException {
            ClassLoader originalLoader = Thread.currentThread().getContextClassLoader();
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
