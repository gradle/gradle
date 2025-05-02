/*
 * Copyright 2015 the original author or authors.
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

public interface CompiledScript<T extends Script, D> {
    /**
     * Returns true if the `run()` method of this script is effectively empty and can be ignored.
     */
    boolean getRunDoesSomething();

    /**
     * Returns true if the script declares any methods.
     */
    boolean getHasMethods();

    Class<? extends T> loadClass();

    D getData();

    /**
     * Called when this script is reused in a new build invocation.
     */
    void onReuse();
}
