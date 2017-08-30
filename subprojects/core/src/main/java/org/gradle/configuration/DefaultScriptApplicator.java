/*
 * Copyright 2017 the original author or authors.
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

import org.gradle.api.initialization.dsl.ScriptHandler;
import org.gradle.api.internal.initialization.ClassLoaderScope;
import org.gradle.api.internal.initialization.ScriptHandlerFactory;
import org.gradle.groovy.scripts.ScriptSource;
import org.gradle.internal.operations.BuildOperationExecutor;

import javax.annotation.Nullable;

public class DefaultScriptApplicator implements ScriptApplicator {

    private final ScriptPluginFactory scriptFactory;
    private final ScriptHandlerFactory scriptHandlerFactory;
    private final BuildOperationExecutor buildOperationExecutor;

    public DefaultScriptApplicator(ScriptPluginFactory scriptFactory, ScriptHandlerFactory scriptHandlerFactory, BuildOperationExecutor buildOperationExecutor) {
        this.scriptFactory = scriptFactory;
        this.scriptHandlerFactory = scriptHandlerFactory;
        this.buildOperationExecutor = buildOperationExecutor;
    }

    @Override
    public void applyTo(Object target, ScriptSource scriptSource, @Nullable ScriptHandler scriptHandler, ClassLoaderScope targetScope, ClassLoaderScope baseScope, boolean topLevelScript) {
        ScriptPlugin scriptPlugin = scriptPluginFor(scriptSource, scriptHandler, targetScope, baseScope, topLevelScript);
        scriptPlugin.apply(target);
    }

    @Override
    public void applyTo(Iterable<Object> targets, ScriptSource scriptSource, ClassLoaderScope targetScope, ClassLoaderScope baseScope) {
        ScriptPlugin scriptPlugin = scriptPluginFor(scriptSource, null, targetScope, baseScope, false);
        for (Object target : targets) {
            scriptPlugin.apply(target);
        }
    }

    private ScriptPlugin scriptPluginFor(ScriptSource scriptSource, ScriptHandler scriptHandler, ClassLoaderScope targetScope, ClassLoaderScope baseScope, boolean topLevelScript) {
        ScriptHandler effectiveScriptHandler = scriptHandler != null ? scriptHandler : scriptHandlerFor(scriptSource, targetScope);
        ScriptPlugin scriptPlugin = scriptFactory.create(scriptSource, effectiveScriptHandler, targetScope, baseScope, topLevelScript);
        return new BuildOperationScriptPlugin(scriptPlugin, buildOperationExecutor);
    }

    private ScriptHandler scriptHandlerFor(ScriptSource scriptSource, ClassLoaderScope targetScope) {
        return scriptHandlerFactory.create(scriptSource, targetScope);
    }
}
