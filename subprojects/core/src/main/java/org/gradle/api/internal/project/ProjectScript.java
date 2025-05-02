/*
 * Copyright 2016 the original author or authors.
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
package org.gradle.api.internal.project;

import groovy.lang.Closure;
import org.gradle.api.initialization.dsl.ScriptHandler;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.LoggingManager;
import org.gradle.internal.logging.StandardOutputCapture;
import org.gradle.plugin.use.internal.PluginsAwareScript;

import java.util.Map;

public abstract class ProjectScript extends PluginsAwareScript {

    @Override
    public void apply(Closure closure) {
        getScriptTarget().apply(closure);
    }

    @Override
    @SuppressWarnings("unchecked")
    public void apply(Map options) {
        getScriptTarget().apply(options);
    }

    @Override
    public ScriptHandler getBuildscript() {
        return getScriptTarget().getBuildscript();
    }

    @Override
    public void buildscript(Closure configureClosure) {
        getScriptTarget().buildscript(configureClosure);
    }

    @Override
    public StandardOutputCapture getStandardOutputCapture() {
        return getScriptTarget().getStandardOutputCapture();
    }

    @Override
    public LoggingManager getLogging() {
        return getScriptTarget().getLogging();
    }

    @Override
    public Logger getLogger() {
        return getScriptTarget().getLogger();
    }

    @Override
    public String toString() {
        return getScriptTarget().toString();
    }

    @Override
    public ProjectInternal getScriptTarget() {
        return (ProjectInternal) super.getScriptTarget();
    }
}
