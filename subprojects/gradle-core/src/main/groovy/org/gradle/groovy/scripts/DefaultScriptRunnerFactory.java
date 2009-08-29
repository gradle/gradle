/*
 * Copyright 2009 the original author or authors.
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

import org.gradle.api.GradleScriptException;
import org.gradle.api.logging.LogLevel;

public class DefaultScriptRunnerFactory implements ScriptRunnerFactory {
    private final ScriptMetaData scriptMetaData;

    public DefaultScriptRunnerFactory(ScriptMetaData scriptMetaData) {
        this.scriptMetaData = scriptMetaData;
    }

    public <T extends Script> ScriptRunner<T> create(T script) {
        return new ScriptRunnerImpl<T>(script);
    }

    private class ScriptRunnerImpl<T extends Script> implements ScriptRunner<T> {
        private final T script;

        public ScriptRunnerImpl(T script) {
            this.script = script;
        }

        public ScriptRunner setDelegate(Object delegate) {
            scriptMetaData.applyMetaData(script, delegate);
            return this;
        }

        public T getScript() {
            return script;
        }

        public void run() throws GradleScriptException {
            ClassLoader originalLoader = Thread.currentThread().getContextClassLoader();
            Thread.currentThread().setContextClassLoader(script.getContextClassloader());
            script.getStandardOutputRedirector().on(LogLevel.QUIET);
            try {
                script.run();
            } catch (Throwable e) {
                throw new GradleScriptException(String.format("A problem occurred evaluating %s.", script), e,
                        script.getScriptSource());
            } finally {
                script.getStandardOutputRedirector().flush();
                Thread.currentThread().setContextClassLoader(originalLoader);
            }
        }
    }
}
