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

import org.gradle.api.internal.project.StandardOutputRedirector;

/**
 * The base class for all scripts executed by Gradle.
 */
public abstract class Script extends groovy.lang.Script {
    private ScriptSource source;

    public ScriptSource getScriptSource() {
        return source;
    }

    public void setScriptSource(ScriptSource source) {
        this.source = source;
    }

    public abstract StandardOutputRedirector getStandardOutputRedirector();

    public abstract ClassLoader getContextClassloader();
}
