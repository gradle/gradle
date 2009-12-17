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

import org.gradle.api.internal.initialization.ScriptClassLoaderProvider;
import org.gradle.groovy.scripts.BasicScript;
import org.gradle.groovy.scripts.ScriptSource;

public interface ScriptObjectConfigurer {
    ScriptSource getSource();
    
    ScriptObjectConfigurer setClasspathClosureName(String name);

    ScriptObjectConfigurer setScriptBaseClass(Class<? extends BasicScript> type);

    ScriptObjectConfigurer setClassLoader(ClassLoader classLoader);

    ScriptObjectConfigurer setClassLoaderProvider(ScriptClassLoaderProvider classLoaderProvider);

    /**
     * Applies the script to the given object. If target object implements {@link org.gradle.groovy.scripts.ScriptAware},
     * will coordinate with the target object to configure classloaders, etc.
     *
     * @param target The target object to configure.
     */
    void apply(Object target);
}
