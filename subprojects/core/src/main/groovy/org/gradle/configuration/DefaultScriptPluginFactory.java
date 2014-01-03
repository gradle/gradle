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

import com.google.common.collect.ImmutableMap;
import org.codehaus.groovy.ast.stmt.Statement;
import org.gradle.api.internal.initialization.ScriptClassLoaderProvider;
import org.gradle.api.internal.initialization.ScriptCompileScope;
import org.gradle.api.internal.initialization.ScriptHandlerFactory;
import org.gradle.api.internal.initialization.ScriptHandlerInternal;
import org.gradle.api.plugins.PluginAware;
import org.gradle.groovy.scripts.*;
import org.gradle.groovy.scripts.internal.BuildScriptTransformer;
import org.gradle.groovy.scripts.internal.FilteredScriptBlockTransformer;
import org.gradle.groovy.scripts.internal.StatementExtractingScriptTransformer;
import org.gradle.internal.Factory;
import org.gradle.internal.Transformers;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.service.DefaultServiceRegistry;
import org.gradle.logging.LoggingManagerInternal;
import org.gradle.plugin.PluginHandler;
import org.gradle.plugin.internal.DefaultPluginHandler;
import org.gradle.plugin.internal.NonPluggableTargetPluginHandler;
import org.gradle.plugin.internal.PluginRequestApplicator;
import org.gradle.plugin.internal.PluginResolutionApplicator;
import org.gradle.plugin.resolve.internal.PluginRequest;
import org.gradle.plugin.resolve.internal.PluginResolver;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class DefaultScriptPluginFactory implements ScriptPluginFactory {
    private final ScriptCompilerFactory scriptCompilerFactory;
    private final ImportsReader importsReader;
    private final ScriptHandlerFactory scriptHandlerFactory;
    private final ScriptCompileScope defaultCompileScope;
    private final Factory<LoggingManagerInternal> loggingManagerFactory;
    private final Instantiator instantiator;
    private final PluginResolver pluginResolver;
    private ClassLoader pluginParentClassLoader;

    public DefaultScriptPluginFactory(ScriptCompilerFactory scriptCompilerFactory,
                                      ImportsReader importsReader,
                                      ScriptHandlerFactory scriptHandlerFactory,
                                      ScriptCompileScope defaultCompileScope,
                                      Factory<LoggingManagerInternal> loggingManagerFactory,
                                      Instantiator instantiator,
                                      PluginResolver pluginResolver,
                                      ClassLoader pluginParentClassLoader) {
        this.scriptCompilerFactory = scriptCompilerFactory;
        this.importsReader = importsReader;
        this.scriptHandlerFactory = scriptHandlerFactory;
        this.defaultCompileScope = defaultCompileScope;
        this.loggingManagerFactory = loggingManagerFactory;
        this.instantiator = instantiator;
        this.pluginResolver = pluginResolver;
        this.pluginParentClassLoader = pluginParentClassLoader;
    }

    public ScriptPlugin create(ScriptSource scriptSource) {
        return new ScriptPluginImpl(scriptSource);
    }

    private class ScriptPluginImpl implements ScriptPlugin {
        private final ScriptSource scriptSource;
        private String classpathClosureName = "buildscript";
        private Class<? extends BasicScript> scriptType = DefaultScript.class;
        private ScriptClassLoaderProvider classLoaderProvider;
        private ScriptCompileScope parentScope = defaultCompileScope;

        public ScriptPluginImpl(ScriptSource scriptSource) {
            this.scriptSource = scriptSource;
        }

        public ScriptSource getSource() {
            return scriptSource;
        }

        public ScriptPlugin setClasspathClosureName(String name) {
            this.classpathClosureName = name;
            return this;
        }

        public ScriptPlugin setParentScope(ScriptCompileScope parentScope) {
            this.parentScope = parentScope;
            return this;
        }

        public ScriptPlugin setClassLoaderProvider(ScriptClassLoaderProvider classLoaderProvider) {
            this.classLoaderProvider = classLoaderProvider;
            return this;
        }

        public ScriptPlugin setScriptBaseClass(Class<? extends BasicScript> type) {
            scriptType = type;
            return this;
        }

        public void apply(final Object target) {
            DefaultServiceRegistry services = new DefaultServiceRegistry();
            services.add(ScriptPluginFactory.class, DefaultScriptPluginFactory.this);
            services.add(LoggingManagerInternal.class, loggingManagerFactory.create());
            services.add(Instantiator.class, instantiator);

            ScriptAware scriptAware = null;
            if (target instanceof ScriptAware) {
                scriptAware = (ScriptAware) target;
                scriptAware.beforeCompile(this);
            }
            ScriptClassLoaderProvider classLoaderProvider = this.classLoaderProvider;
            ScriptSource withImports = importsReader.withImports(scriptSource);

            if (classLoaderProvider == null) {
                ScriptHandlerInternal defaultScriptHandler = scriptHandlerFactory.create(withImports, parentScope);
                services.add(ScriptHandlerInternal.class, defaultScriptHandler);
                classLoaderProvider = defaultScriptHandler;
            }

            List<PluginRequest> pluginRequests = new LinkedList<PluginRequest>();
            if (target instanceof PluginAware) {
                services.add(PluginHandler.class, new DefaultPluginHandler(pluginRequests));
            } else {
                services.add(PluginHandler.class, new NonPluggableTargetPluginHandler(target));
            }

            ScriptCompiler compiler = scriptCompilerFactory.createCompiler(withImports);

            compiler.setClassloader(classLoaderProvider.getBaseCompilationClassLoader());

            Map<String, org.gradle.api.Transformer<Statement, Statement>> blockTransforms = ImmutableMap.of(
                    classpathClosureName, Transformers.<Statement>noOpTransformer(),
                    "plugins", new ScriptBlockToServiceConfigurationTransformer(DefaultScript.SCRIPT_SERVICES_PROPERTY, PluginHandler.class)
            );

            FilteredScriptBlockTransformer scriptBlockTransformer = new FilteredScriptBlockTransformer(blockTransforms);

            StatementExtractingScriptTransformer classpathScriptTransformer = new StatementExtractingScriptTransformer(classpathClosureName, scriptBlockTransformer);

            compiler.setTransformer(classpathScriptTransformer);

            ScriptRunner<? extends BasicScript> classPathScriptRunner = compiler.compile(scriptType);
            classPathScriptRunner.getScript().init(target, services);
            classPathScriptRunner.run();

            if (!pluginRequests.isEmpty()) {
                @SuppressWarnings("ConstantConditions")
                PluginResolutionApplicator resolutionApplicator = new PluginResolutionApplicator((PluginAware) target, pluginParentClassLoader, classLoaderProvider);
                PluginRequestApplicator requestApplicator = new PluginRequestApplicator(pluginResolver, resolutionApplicator);
                requestApplicator.applyPlugin(pluginRequests);
            }

            classLoaderProvider.updateClassPath();
            compiler.setClassloader(classLoaderProvider.getScriptCompileClassLoader());

            compiler.setTransformer(new BuildScriptTransformer("no_" + classpathScriptTransformer.getId(), classpathScriptTransformer.invert()));
            ScriptRunner<? extends BasicScript> runner = compiler.compile(scriptType);

            runner.getScript().init(target, services);
            if (scriptAware != null) {
                scriptAware.afterCompile(this, runner.getScript());
            }
            runner.run();
        }

    }
}