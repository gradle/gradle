/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.api.internal.initialization;

import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.groovy.scripts.ScriptSource;
import org.gradle.internal.Factory;

public class ScriptHandlerClassLoaderFactory implements Factory<ClassLoader> {

    private static final Logger LOGGER = Logging.getLogger(ScriptHandlerClassLoaderFactory.class);

    private final ScriptSource scriptSource;
    private final ClassLoaderScope classLoaderScope;

    public ScriptHandlerClassLoaderFactory(ScriptSource scriptSource, ClassLoaderScope classLoaderScope) {
        this.scriptSource = scriptSource;
        this.classLoaderScope = classLoaderScope;
    }

    public ClassLoader create() {
        if (!classLoaderScope.isLocked()) {
            LOGGER.debug("Eager creation of script class loader for {}. This may result in performance issues.", scriptSource);
        }
        return classLoaderScope.getLocalClassLoader();
    }

}
