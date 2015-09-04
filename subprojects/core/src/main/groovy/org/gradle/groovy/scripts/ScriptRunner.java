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
import org.gradle.internal.service.ServiceRegistry;

/**
 * Executes a script of type T.
 */
public interface ScriptRunner<T extends Script, M> {
    /**
     * Returns the script which will be executed by this runner. This method is relatively expensive.
     *
     * @return the script.
     */
    T getScript();

    /**
     * Returns the data extracted at compilation time.
     */
    M getData();

    /**
     * Returns true when the script will run some code when executed. Returns false for a script whose `run()` method is effectively empty.
     */
    boolean getRunDoesSomething();

    /**
     * Returns true when the script defines some methods.
     */
    boolean getHasMethods();

    /**
     * Executes the script. This is generally more efficient than using {@link #getScript()}.
     *
     * @throws GradleScriptException On execution failure.
     */
    void run(Object target, ServiceRegistry scriptServices) throws GradleScriptException;
}
