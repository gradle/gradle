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
package org.gradle.launcher.exec;

import org.gradle.initialization.BuildAction;

public interface BuildActionExecuter<P> {
    /**
     * Executes the given action, and returns the result.
     *
     * @param action The action
     * @param <T> The result type
     * @return The result.
     */
    <T> T execute(BuildAction<T> action, P actionParameters);
}
