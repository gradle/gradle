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
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.plugin.use.internal.PluginsAwareScript;
import org.jspecify.annotations.Nullable;

import java.util.Map;

public abstract class ProjectScript extends PluginsAwareScript {

    @Nullable
    private LoggingManager loggingManager = null;
    @Nullable
    private Logger logger = null;
    @Nullable
    private StandardOutputCapture standardOutputCapture = null;

    @Override
    public void init(Object target, ServiceRegistry services) {
        super.init(target, services);

        // Materialize the project-scoped logging services into fields so the implementation
        // does not delegate to the live target on `getLogging`; this lets us replace the
        // target with a broken-object stub when deserialized from CC.
        //
        // init() runs twice: once with a ProjectScriptTarget wrapper (method-inheritance setup) and
        // once with the real Project (by the script runner, always before run()). Only the latter
        // carries the model to materialize from, and it is the state the running/captured script keeps.
        if (target instanceof ProjectInternal) {
            loggingManager = getScriptTarget().getLogging();
            logger = getScriptTarget().getLogger();
            standardOutputCapture = getScriptTarget().getStandardOutputCapture();
        }
    }

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
        assert standardOutputCapture != null : "the field must be initialized by init";
        return standardOutputCapture;
    }

    @Override
    public LoggingManager getLogging() {
        assert loggingManager != null : "the field must be initialized by init";
        return loggingManager;
    }

    @Override
    public Logger getLogger() {
        assert logger != null : "the field must be initialized by init";
        return logger;
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
