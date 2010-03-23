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

import org.gradle.api.Plugin;
import org.gradle.api.internal.initialization.ScriptClassLoaderProvider;
import org.gradle.groovy.scripts.BasicScript;
import org.gradle.groovy.scripts.ScriptSource;

public interface ScriptPlugin extends Plugin<Object> {
    ScriptSource getSource();

    ScriptPlugin setClasspathClosureName(String name);

    ScriptPlugin setScriptBaseClass(Class<? extends BasicScript> type);

    ScriptPlugin setClassLoader(ClassLoader classLoader);

    ScriptPlugin setClassLoaderProvider(ScriptClassLoaderProvider classLoaderProvider);

    /**
     * Applies the script to the given object. If target object implements {@link org.gradle.groovy.scripts.ScriptAware},
     * will call {@link org.gradle.groovy.scripts.ScriptAware#beforeCompile(ScriptPlugin)} and {@link
     * org.gradle.groovy.scripts.ScriptAware#afterCompile(ScriptPlugin , org.gradle.groovy.scripts.Script)}.
     *
     * @param target The target object to configure.
     */
    void apply(Object target);
}
