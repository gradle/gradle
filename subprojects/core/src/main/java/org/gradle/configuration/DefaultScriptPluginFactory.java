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

package org.gradle.configuration;

import org.gradle.api.initialization.dsl.ScriptHandler;
import org.gradle.api.internal.GradleInternal;
import org.gradle.api.internal.SettingsInternal;
import org.gradle.api.internal.initialization.ClassLoaderScope;
import org.gradle.api.internal.initialization.ScriptHandlerInternal;
import org.gradle.api.internal.plugins.PluginManagerInternal;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.groovy.scripts.BasicScript;
import org.gradle.groovy.scripts.ScriptCompiler;
import org.gradle.groovy.scripts.ScriptCompilerFactory;
import org.gradle.groovy.scripts.ScriptRunner;
import org.gradle.groovy.scripts.ScriptSource;
import org.gradle.groovy.scripts.internal.BuildScriptData;
import org.gradle.groovy.scripts.internal.CompileOperation;
import org.gradle.internal.Actions;
import org.gradle.internal.Factory;
import org.gradle.internal.logging.LoggingManagerInternal;
import org.gradle.internal.service.CloseableServiceRegistry;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.internal.service.ServiceRegistryBuilder;
import org.gradle.model.dsl.internal.transform.ClosureCreationInterceptingVerifier;
import org.gradle.plugin.management.internal.PluginHandler;
import org.gradle.plugin.management.internal.PluginRequests;
import org.gradle.plugin.use.internal.PluginRequestApplicator;
import org.gradle.plugin.use.internal.PluginsAwareScript;

public class DefaultScriptPluginFactory implements ScriptPluginFactory {
    private final ServiceRegistry scriptServices;
    private final ScriptCompilerFactory scriptCompilerFactory;
    private final Factory<LoggingManagerInternal> loggingFactoryManager;
    private final PluginHandler pluginHandler;
    private final PluginRequestApplicator pluginRequestApplicator;
    private final CompileOperationFactory compileOperationFactory;
    private ScriptPluginFactory scriptPluginFactory;

    public DefaultScriptPluginFactory(
        ServiceRegistry scriptServices, ScriptCompilerFactory scriptCompilerFactory, Factory<LoggingManagerInternal> loggingFactoryManager,
        PluginHandler pluginHandler, PluginRequestApplicator pluginRequestApplicator,
        CompileOperationFactory compileOperationFactory
    ) {
        this.scriptServices = scriptServices;
        this.scriptCompilerFactory = scriptCompilerFactory;
        this.loggingFactoryManager = loggingFactoryManager;
        this.pluginHandler = pluginHandler;
        this.pluginRequestApplicator = pluginRequestApplicator;
        this.compileOperationFactory = compileOperationFactory;
        this.scriptPluginFactory = this;
    }

    public void setScriptPluginFactory(ScriptPluginFactory scriptPluginFactory) {
        this.scriptPluginFactory = scriptPluginFactory;
    }

    @Override
    public ScriptPlugin create(ScriptSource scriptSource, ScriptHandler scriptHandler, ClassLoaderScope targetScope, ClassLoaderScope baseScope, boolean topLevelScript) {
        return new ScriptPluginImpl(scriptSource, (ScriptHandlerInternal) scriptHandler, targetScope, baseScope, topLevelScript);
    }

    private class ScriptPluginImpl implements ScriptPlugin {
        private final ScriptSource scriptSource;
        private final ClassLoaderScope targetScope;
        private final ClassLoaderScope baseScope;
        private final ScriptHandlerInternal scriptHandler;
        private final boolean topLevelScript;

        public ScriptPluginImpl(ScriptSource scriptSource, ScriptHandlerInternal scriptHandler, ClassLoaderScope targetScope, ClassLoaderScope baseScope, boolean topLevelScript) {
            this.scriptSource = scriptSource;
            this.targetScope = targetScope;
            this.baseScope = baseScope;
            this.scriptHandler = scriptHandler;
            this.topLevelScript = topLevelScript;
        }

        @Override
        public ScriptSource getSource() {
            return scriptSource;
        }

        @Override
        public void apply(final Object target) {
            CloseableServiceRegistry services = ServiceRegistryBuilder.builder()
                .displayName("script plugin services")
                .parent(scriptServices)
                .provider(registration -> {
                    registration.add(ScriptPluginFactory.class, scriptPluginFactory);
                    registration.add(ClassLoaderScope.class, baseScope);
                    registration.add(LoggingManagerInternal.class, loggingFactoryManager.create());
                    registration.add(ScriptHandler.class, scriptHandler);
                })
                .build();

            final ScriptTarget initialPassScriptTarget = initialPassTarget(target);

            ScriptCompiler compiler = scriptCompilerFactory.createCompiler(scriptSource);

            // Pass 1, extract plugin requests and plugin repositories and execute buildscript {}, ignoring (i.e. not even compiling) anything else
            CompileOperation<?> initialOperation = compileOperationFactory.getPluginsBlockCompileOperation(initialPassScriptTarget);
            Class<? extends BasicScript> scriptType = initialPassScriptTarget.getScriptClass();
            ScriptRunner<? extends BasicScript, ?> initialRunner = compiler.compile(scriptType, target, baseScope, initialOperation, Actions.doNothing());
            initialRunner.run(target, services);

            PluginManagerInternal pluginManager = topLevelScript ? initialPassScriptTarget.getPluginManager() : null;
            PluginRequests initialPluginRequests = getInitialPluginRequests(initialRunner);
            PluginRequests allPluginRequests = pluginHandler.getAllPluginRequests(initialPluginRequests, target);
            pluginRequestApplicator.applyPlugins(allPluginRequests, scriptHandler, pluginManager, targetScope);

            // Pass 2, compile everything except buildscript {}, pluginManagement{}, and plugin requests, then run
            final ScriptTarget scriptTarget = secondPassTarget(target);
            scriptType = scriptTarget.getScriptClass();

            CompileOperation<BuildScriptData> operation = compileOperationFactory.getScriptCompileOperation(scriptSource, scriptTarget);

            final ScriptRunner<? extends BasicScript, BuildScriptData> runner = compiler.compile(scriptType, target, targetScope, operation, ClosureCreationInterceptingVerifier.INSTANCE);
            if (scriptTarget.getSupportsMethodInheritance() && runner.getHasMethods()) {
                BasicScript script = runner.getScript();
                script.init(scriptTarget, scriptServices);
                scriptTarget.attachScript(script);
            }
            if (!runner.getRunDoesSomething()) {
                return;
            }

            Runnable buildScriptRunner = () -> runner.run(target, services);

            boolean hasImperativeStatements = runner.getData().getHasImperativeStatements();
            scriptTarget.addConfiguration(buildScriptRunner, !hasImperativeStatements);
        }

        private ScriptTarget initialPassTarget(Object target) {
            return wrap(target, true /* isInitialPass */);
        }

        private ScriptTarget secondPassTarget(Object target) {
            return wrap(target, false /* isInitialPass */);
        }

        private ScriptTarget wrap(Object target, boolean isInitialPass) {
            if (target instanceof ProjectInternal && topLevelScript) {
                // Only use this for top level project scripts
                return new ProjectScriptTarget((ProjectInternal) target);
            }
            if (target instanceof GradleInternal && topLevelScript) {
                // Only use this for top level init scripts
                return new InitScriptTarget((GradleInternal) target);
            }
            if (target instanceof SettingsInternal && topLevelScript) {
                // Only use this for top level settings scripts
                if (isInitialPass) {
                    return new InitialPassSettingScriptTarget((SettingsInternal) target);
                } else {
                    return new SettingScriptTarget((SettingsInternal) target);
                }
            } else {
                return new DefaultScriptTarget(target);
            }
        }
    }

    // TODO This is not nice: work out a better way to collect the plugin requests from invoking the plugins block.
    private PluginRequests getInitialPluginRequests(ScriptRunner<? extends BasicScript, ?> initialRunner) {
        if (initialRunner.getRunDoesSomething()) {
            BasicScript script = initialRunner.getScript();
            if (script instanceof PluginsAwareScript) {
                return ((PluginsAwareScript) script).getPluginRequests();
            }
        }
        return PluginRequests.EMPTY;
    }
}
