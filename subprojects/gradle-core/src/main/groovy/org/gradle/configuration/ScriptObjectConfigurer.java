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
package org.gradle.configuration;

import org.gradle.api.Action;
import org.gradle.api.internal.initialization.ScriptClassLoaderProvider;
import org.gradle.groovy.scripts.Script;

public interface ScriptObjectConfigurer {
    ScriptObjectConfigurer setClassLoaderProvider(ScriptClassLoaderProvider provider);

    ScriptObjectConfigurer setInitAction(Action<? super Script> initAction);

    ScriptObjectConfigurer setClasspathClosureName(String name);

    ScriptObjectConfigurer setScriptBaseClass(Class<? extends Script> baseClass);

    /**
     * Applies the configuration to the given object.
     *
     * @param target The target object to configure.
     */
    void apply(Object target);
}
