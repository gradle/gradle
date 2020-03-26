/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.plugin.devel.internal.precompiled;

import org.gradle.api.internal.initialization.ClassLoaderScope;
import org.gradle.api.internal.initialization.ScriptHandlerFactory;
import org.gradle.api.internal.initialization.ScriptHandlerInternal;
import org.gradle.configuration.CompileOperationFactory;
import org.gradle.configuration.DefaultScriptTarget;
import org.gradle.configuration.ScriptTarget;
import org.gradle.groovy.scripts.BasicScript;
import org.gradle.groovy.scripts.ScriptRunner;
import org.gradle.groovy.scripts.ScriptSource;
import org.gradle.groovy.scripts.internal.BuildScriptData;
import org.gradle.groovy.scripts.internal.CompileOperation;
import org.gradle.groovy.scripts.internal.CompiledScript;
import org.gradle.groovy.scripts.internal.ScriptCompilationHandler;
import org.gradle.groovy.scripts.internal.ScriptRunnerFactory;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.hash.HashCode;
import org.gradle.internal.resource.StringTextResource;
import org.gradle.internal.resource.TextResource;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.plugin.management.internal.PluginRequests;
import org.gradle.plugin.management.internal.autoapply.AutoAppliedPluginHandler;
import org.gradle.plugin.use.internal.PluginRequestApplicator;
import org.gradle.plugin.use.internal.PluginsAwareScript;

import java.io.File;
import java.net.URISyntaxException;


class PreCompiledScriptRunner {

    private final Object target;
    private final ServiceRegistry serviceRegistry;

    private final CompileOperationFactory compileOperationFactory;
    private final ScriptCompilationHandler scriptCompilationHandler;
    private final ScriptRunnerFactory scriptRunnerFactory;
    private final ScriptHandlerFactory scriptHandlerFactory;
    private final AutoAppliedPluginHandler autoAppliedPluginHandler;
    private final PluginRequestApplicator pluginRequestApplicator;

    private final ScriptSource scriptSource;
    private final ScriptSource pluginsBlockSource;

    private final ClassLoaderScope classLoaderScope;
    private final ClassLoader classLoader;

    private final ScriptTarget scriptTarget;

    private final File jarFile;
    private final Class<?> scriptClass;
    private final HashCode hashCode;

    PreCompiledScriptRunner(Object target,
                            ServiceRegistry serviceRegistry,
                            ClassLoaderScope classLoaderScope,
                            Class<?> precompiledScriptClass,
                            String hashCode) {
        this.target = target;
        this.serviceRegistry = serviceRegistry;

        this.compileOperationFactory = serviceRegistry.get(CompileOperationFactory.class);
        this.scriptCompilationHandler = serviceRegistry.get(ScriptCompilationHandler.class);
        this.scriptRunnerFactory = serviceRegistry.get(ScriptRunnerFactory.class);
        this.scriptHandlerFactory = serviceRegistry.get(ScriptHandlerFactory.class);
        this.autoAppliedPluginHandler = serviceRegistry.get(AutoAppliedPluginHandler.class);
        this.pluginRequestApplicator = serviceRegistry.get(PluginRequestApplicator.class);

        this.classLoaderScope = classLoaderScope;
        this.classLoaderScope.lock();
        this.classLoader = classLoaderScope.getExportClassLoader();

        this.scriptTarget = new DefaultScriptTarget(target);

        this.scriptClass = precompiledScriptClass;
        try {
            this.jarFile = new File(scriptClass.getProtectionDomain().getCodeSource().getLocation().toURI());
        } catch (URISyntaxException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }

        this.hashCode = HashCode.fromString(hashCode);

        this.scriptSource = new ScriptSource() {
            @Override
            public String getClassName() {
                return scriptClass.getSimpleName();
            }

            @Override
            public TextResource getResource() {
                return new StringTextResource(scriptClass.getSimpleName(), "");
            }

            @Override
            public String getFileName() {
                return null;
            }

            @Override
            public String getDisplayName() {
                return scriptClass.getSimpleName();
            }
        };

        this.pluginsBlockSource = new PluginsBlockSourceWrapper(scriptSource);
    }

    public void run() {
        applyPlugins(extractPlugins());
        executeScript();
    }

    private PluginRequests extractPlugins() {
        CompileOperation<?> pluginRequestsCompileOperation = compileOperationFactory.getPluginRequestsCompileOperation(scriptTarget);
        CompiledScript<? extends BasicScript, ?> compiledPluginRequests = scriptCompilationHandler.loadFromClasspath(
            pluginsBlockSource, hashCode, classLoaderScope, jarFile, pluginsBlockSource.getClassName(),
            pluginRequestsCompileOperation, scriptTarget.getScriptClass(), scriptClass);
        ScriptRunner<? extends BasicScript, ?> initialRunner = scriptRunnerFactory.create(compiledPluginRequests, pluginsBlockSource, classLoader);
        initialRunner.run(target, serviceRegistry);
        PluginRequests initialPluginRequests = getInitialPluginRequests(initialRunner);
        return autoAppliedPluginHandler.mergeWithAutoAppliedPlugins(initialPluginRequests, target);
    }

    private void applyPlugins(PluginRequests pluginRequests) {
        ScriptHandlerInternal scriptHandler = scriptHandlerFactory.create(pluginsBlockSource, classLoaderScope);
        pluginRequestApplicator.applyPlugins(pluginRequests, scriptHandler, scriptTarget.getPluginManager(), classLoaderScope);
    }

    private void executeScript() {
        CompileOperation<BuildScriptData> buildScriptDataCompileOperation = compileOperationFactory.getBuildScriptDataCompileOperation(scriptSource, scriptTarget);
        CompiledScript<? extends BasicScript, BuildScriptData> compiledScript = scriptCompilationHandler.loadFromClasspath(
            scriptSource, hashCode, classLoaderScope, jarFile, scriptSource.getClassName(),
            buildScriptDataCompileOperation, scriptTarget.getScriptClass(), scriptClass);
        ScriptRunner<? extends BasicScript, BuildScriptData> runner = scriptRunnerFactory.create(compiledScript, scriptSource, classLoader);
        runner.run(target, serviceRegistry);
    }

    private static PluginRequests getInitialPluginRequests(ScriptRunner<? extends BasicScript, ?> initialRunner) {
        if (initialRunner.getRunDoesSomething()) {
            BasicScript script = initialRunner.getScript();
            if (script instanceof PluginsAwareScript) {
                return ((PluginsAwareScript) script).getPluginRequests();
            }
        }
        return PluginRequests.EMPTY;
    }

}
