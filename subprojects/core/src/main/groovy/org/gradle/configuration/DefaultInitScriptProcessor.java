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

import org.gradle.api.initialization.dsl.ScriptHandler;
import org.gradle.api.internal.GradleInternal;
import org.gradle.api.internal.initialization.ClassLoaderScope;
import org.gradle.api.internal.initialization.ScriptHandlerFactory;
import org.gradle.groovy.scripts.ScriptSource;
import org.gradle.initialization.InitScript;

/**
 * Processes (and runs) an init script for a specified build.  Handles defining
 * the classpath based on the initscript {} configuration closure.
 */
public class DefaultInitScriptProcessor implements InitScriptProcessor {
    private final ScriptPluginFactory configurerFactory;
    private final ScriptHandlerFactory scriptHandlerFactory;

    public DefaultInitScriptProcessor(ScriptPluginFactory configurerFactory, ScriptHandlerFactory scriptHandlerFactory) {
        this.configurerFactory = configurerFactory;
        this.scriptHandlerFactory = scriptHandlerFactory;
    }

    public void process(final ScriptSource initScript, GradleInternal gradle) {
        ClassLoaderScope baseScope = gradle.getClassLoaderScope();
        ClassLoaderScope scriptScope = baseScope.createChild();
        ScriptHandler scriptHandler = scriptHandlerFactory.create(initScript, scriptScope);
        ScriptPlugin configurer = configurerFactory.create(initScript, scriptHandler, scriptScope, baseScope, "initscript", InitScript.class, false);
        configurer.apply(gradle);
    }
}