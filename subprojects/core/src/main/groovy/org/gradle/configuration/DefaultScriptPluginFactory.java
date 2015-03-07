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
import org.gradle.api.internal.file.FileLookup;
import org.gradle.api.internal.initialization.ClassLoaderScope;
import org.gradle.api.internal.initialization.ScriptHandlerFactory;
import org.gradle.api.internal.plugins.PluginAwareInternal;
import org.gradle.api.internal.plugins.PluginManagerInternal;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.project.ProjectScript;
import org.gradle.groovy.scripts.*;
import org.gradle.groovy.scripts.internal.*;
import org.gradle.internal.Actions;
import org.gradle.internal.Factory;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.serialize.BaseSerializerFactory;
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

    public ScriptPlugin create(ScriptSource scriptSource, ScriptHandler scriptHandler, ClassLoaderScope targetScope, ClassLoaderScope baseScope, String classpathClosureName, Class<? extends BasicScript> scriptClass, boolean ownerScript) {
        return new ScriptPluginImpl(scriptSource, scriptHandler, targetScope, baseScope, classpathClosureName, scriptClass, ownerScript);
    }

    private class ScriptPluginImpl implements ScriptPlugin {
        private final ScriptSource scriptSource;
        private final ClassLoaderScope targetScope;
        private final ClassLoaderScope baseScope;
        private final String classpathClosureName;
        private final Class<? extends BasicScript> scriptType;
        private final ScriptHandler scriptHandler;
        private final boolean ownerScript;

        public ScriptPluginImpl(ScriptSource scriptSource, ScriptHandler scriptHandler, ClassLoaderScope targetScope, ClassLoaderScope baseScope, String classpathClosureName, Class<? extends BasicScript> scriptType, boolean ownerScript) {
            this.scriptSource = scriptSource;
            this.targetScope = targetScope;
            this.baseScope = baseScope;
            this.classpathClosureName = classpathClosureName;
            this.scriptHandler = scriptHandler;
            this.scriptType = scriptType;
            this.ownerScript = ownerScript;
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

            final ScriptCompiler compiler = scriptCompilerFactory.createCompiler(scriptSource);

            // Pass 1, extract plugin requests and execute buildscript {}, ignoring (i.e. not even compiling) anything else

            boolean supportsPluginsBlock = ProjectScript.class.isAssignableFrom(scriptType);
            String onPluginBlockError = supportsPluginsBlock ? null : "Only Project build scripts can contain plugins {} blocks";

            InitialPassStatementTransformer initialPassStatementTransformer = new InitialPassStatementTransformer(classpathClosureName, onPluginBlockError, scriptSource, documentationRegistry);
            SubsetScriptTransformer initialTransformer = new SubsetScriptTransformer(initialPassStatementTransformer);
            CompileOperation<PluginRequests> initialOperation = new FactoryBackedCompileOperation<PluginRequests>(classpathClosureName, initialTransformer, initialPassStatementTransformer, PluginRequestsSerializer.INSTANCE);

            ScriptRunner<? extends BasicScript, PluginRequests> initialRunner = compiler.compile(scriptType, initialOperation, baseScope.getExportClassLoader(), classpathClosureName, Actions.doNothing());
            initialRunner.getScript().init(target, services);
            initialRunner.run();

            PluginRequests pluginRequests = initialRunner.getCompiledScript().getData();
            PluginManagerInternal pluginManager = target instanceof PluginAwareInternal ? ((PluginAwareInternal) target).getPluginManager() : null;
            pluginRequestApplicator.applyPlugins(pluginRequests, scriptHandler, pluginManager, targetScope);

            // Pass 2, compile everything except buildscript {} and plugin requests, then run

            BuildScriptTransformer buildScriptTransformer = new BuildScriptTransformer(classpathClosureName, scriptSource);
            String operationId = "no_" + classpathClosureName;
            if (ModelBlockTransformer.isEnabled()) {
                operationId = "m_".concat(operationId);
            }
            CompileOperation<Boolean> operation = new FactoryBackedCompileOperation<Boolean>(operationId, buildScriptTransformer, buildScriptTransformer, BaseSerializerFactory.BOOLEAN_SERIALIZER);

            final ScriptRunner<? extends BasicScript, Boolean> runner = compiler.compile(scriptType, operation, targetScope.getLocalClassLoader(), classpathClosureName, ClosureCreationInterceptingVerifier.INSTANCE);

            Runnable buildScriptRunner = new Runnable() {
                public void run() {
                    BasicScript script = runner.getScript();
                    script.init(target, services);
                    if (ownerScript && target instanceof ScriptAware) {
                        ((ScriptAware) target).setScript(script);
                    }
                    runner.run();
                }
            };

            Boolean hasImperativeStatements = runner.getCompiledScript().getData();
            if (!hasImperativeStatements && target instanceof ProjectInternal) {
                ((ProjectInternal) target).addDeferredConfiguration(buildScriptRunner);
            } else {
                buildScriptRunner.run();
            }
        }
    }
}