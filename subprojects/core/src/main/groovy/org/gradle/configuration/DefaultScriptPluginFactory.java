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
import org.gradle.api.internal.DocumentationRegistry;
import org.gradle.api.internal.GradleInternal;
import org.gradle.api.internal.SettingsInternal;
import org.gradle.api.internal.file.FileLookup;
import org.gradle.api.internal.initialization.ClassLoaderScope;
import org.gradle.api.internal.initialization.ScriptHandlerFactory;
import org.gradle.api.internal.initialization.ScriptHandlerInternal;
import org.gradle.api.internal.plugins.PluginManagerInternal;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.groovy.scripts.*;
import org.gradle.groovy.scripts.internal.*;
import org.gradle.internal.Actions;
import org.gradle.internal.Factory;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.service.DefaultServiceRegistry;
import org.gradle.logging.LoggingManagerInternal;
import org.gradle.model.dsl.internal.transform.ClosureCreationInterceptingVerifier;
import org.gradle.model.dsl.internal.transform.ModelBlockTransformer;
import org.gradle.model.internal.inspect.ModelRuleSourceDetector;
import org.gradle.plugin.use.internal.PluginRequestApplicator;
import org.gradle.plugin.use.internal.PluginRequests;
import org.gradle.plugin.use.internal.PluginRequestsSerializer;

public class DefaultScriptPluginFactory implements ScriptPluginFactory {

    private final ScriptCompilerFactory scriptCompilerFactory;
    private final Factory<LoggingManagerInternal> loggingManagerFactory;
    private final Instantiator instantiator;
    private final ScriptHandlerFactory scriptHandlerFactory;
    private final PluginRequestApplicator pluginRequestApplicator;
    private final FileLookup fileLookup;
    private final DocumentationRegistry documentationRegistry;
    private final ModelRuleSourceDetector modelRuleSourceDetector;
    private final BuildScriptDataSerializer buildScriptDataSerializer = new BuildScriptDataSerializer();
    private final PluginRequestsSerializer pluginRequestsSerializer = new PluginRequestsSerializer();

    public DefaultScriptPluginFactory(ScriptCompilerFactory scriptCompilerFactory,
                                      Factory<LoggingManagerInternal> loggingManagerFactory,
                                      Instantiator instantiator,
                                      ScriptHandlerFactory scriptHandlerFactory,
                                      PluginRequestApplicator pluginRequestApplicator,
                                      FileLookup fileLookup,
                                      DocumentationRegistry documentationRegistry,
                                      ModelRuleSourceDetector modelRuleSourceDetector) {
        this.scriptCompilerFactory = scriptCompilerFactory;
        this.loggingManagerFactory = loggingManagerFactory;
        this.instantiator = instantiator;
        this.scriptHandlerFactory = scriptHandlerFactory;
        this.pluginRequestApplicator = pluginRequestApplicator;
        this.fileLookup = fileLookup;
        this.documentationRegistry = documentationRegistry;
        this.modelRuleSourceDetector = modelRuleSourceDetector;
    }

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

        public ScriptSource getSource() {
            return scriptSource;
        }

        public void apply(final Object target) {
            final DefaultServiceRegistry services = new DefaultServiceRegistry();
            services.add(ScriptPluginFactory.class, DefaultScriptPluginFactory.this);
            services.add(ScriptHandlerFactory.class, scriptHandlerFactory);
            services.add(ClassLoaderScope.class, targetScope);
            services.add(LoggingManagerInternal.class, loggingManagerFactory.create());
            services.add(Instantiator.class, instantiator);
            services.add(ScriptHandler.class, scriptHandler);
            services.add(FileLookup.class, fileLookup);
            services.add(ModelRuleSourceDetector.class, modelRuleSourceDetector);

            final ScriptTarget scriptTarget = wrap(target);

            ScriptCompiler compiler = scriptCompilerFactory.createCompiler(scriptSource);

            // Pass 1, extract plugin requests and execute buildscript {}, ignoring (i.e. not even compiling) anything else

            Class<? extends BasicScript> scriptType = scriptTarget.getScriptClass();
            boolean supportsPluginsBlock = scriptTarget.getSupportsPluginsBlock();
            String onPluginBlockError = supportsPluginsBlock ? null : "Only Project build scripts can contain plugins {} blocks";
            String classpathClosureName = scriptTarget.getClasspathBlockName();
            InitialPassStatementTransformer initialPassStatementTransformer = new InitialPassStatementTransformer(classpathClosureName, onPluginBlockError, scriptSource, documentationRegistry);
            SubsetScriptTransformer initialTransformer = new SubsetScriptTransformer(initialPassStatementTransformer);
            CompileOperation<PluginRequests> initialOperation = new FactoryBackedCompileOperation<PluginRequests>("cp_" + scriptTarget.getId(), initialTransformer, initialPassStatementTransformer, pluginRequestsSerializer);

            ScriptRunner<? extends BasicScript, PluginRequests> initialRunner = compiler.compile(scriptType, initialOperation, baseScope.getExportClassLoader(), Actions.doNothing());
            initialRunner.run(target, services);

            PluginRequests pluginRequests = initialRunner.getData();
            PluginManagerInternal pluginManager = scriptTarget.getPluginManager();
            pluginRequestApplicator.applyPlugins(pluginRequests, scriptHandler, pluginManager, targetScope);

            // Pass 2, compile everything except buildscript {} and plugin requests, then run

            BuildScriptTransformer buildScriptTransformer = new BuildScriptTransformer(classpathClosureName, scriptSource);
            String operationId = scriptTarget.getId();
            if (ModelBlockTransformer.isEnabled()) {
                operationId = "m_".concat(operationId);
            }
            CompileOperation<BuildScriptData> operation = new FactoryBackedCompileOperation<BuildScriptData>(operationId, buildScriptTransformer, buildScriptTransformer, buildScriptDataSerializer);

            final ScriptRunner<? extends BasicScript, BuildScriptData> runner = compiler.compile(scriptType, operation, targetScope.getLocalClassLoader(), ClosureCreationInterceptingVerifier.INSTANCE);
            if (scriptTarget.getSupportsMethodInheritance() && runner.getHasMethods()) {
                scriptTarget.attachScript(runner.getScript());
            }
            if (!runner.getRunDoesSomething()) {
                return;
            }

            Runnable buildScriptRunner = new Runnable() {
                public void run() {
                    runner.run(target, services);
                }
            };

            boolean hasImperativeStatements = runner.getData().getHasImperativeStatements();
            scriptTarget.addConfiguration(buildScriptRunner, !hasImperativeStatements);
        }

        private ScriptTarget wrap(Object target) {
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
                return new SettingScriptTarget((SettingsInternal) target);
            } else {
                return new DefaultScriptTarget(target);
            }
        }
    }
}