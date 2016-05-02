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

package org.gradle.groovy.scripts;

import org.gradle.internal.service.ServiceRegistry;
import org.gradle.internal.logging.StandardOutputCapture;

/**
 * The base class for all scripts executed by Gradle.
 */
public abstract class Script extends groovy.lang.Script {
    private ScriptSource source;
    private ClassLoader contextClassloader;

    public ScriptSource getScriptSource() {
        return source;
    }

    public void setScriptSource(ScriptSource source) {
        this.source = source;
    }

    public void setContextClassloader(ClassLoader contextClassloader) {
        this.contextClassloader = contextClassloader;
    }

    public ClassLoader getContextClassloader() {
        return contextClassloader;
    }

    public abstract void init(Object target, ServiceRegistry services);

    public abstract StandardOutputCapture getStandardOutputCapture();

    @Override
    public Object getProperty(String property) {
        // Refactoring of groovy.lang.Script.getProperty logic
        // to avoid unnecessary MissingPropertyException in binding variable lookup
        if (getBinding().hasVariable(property)) {
            return super.getProperty(property);
        } else {
            return getMetaClass().getProperty(this, property);
        }
    }
}
