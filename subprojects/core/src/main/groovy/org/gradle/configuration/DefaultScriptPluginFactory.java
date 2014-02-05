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

import org.gradle.api.internal.initialization.ScriptClassLoaderProvider;
import org.gradle.api.internal.initialization.ScriptHandlerFactory;
import org.gradle.api.internal.initialization.ScriptHandlerInternal;
import org.gradle.api.plugins.PluginAware;
import org.gradle.groovy.scripts.*;
import org.gradle.groovy.scripts.internal.BuildScriptTransformer;
import org.gradle.groovy.scripts.internal.PluginsAndBuildscriptTransformer;
import org.gradle.groovy.scripts.internal.StatementExtractingScriptTransformer;
import org.gradle.internal.Factory;
import org.gradle.internal.classloader.MultiParentClassLoader;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.service.DefaultServiceRegistry;
import org.gradle.logging.LoggingManagerInternal;
import org.gradle.plugin.PluginHandler;
import org.gradle.plugin.internal.*;
import org.gradle.plugin.resolve.internal.PluginRequest;
import org.gradle.plugin.resolve.internal.PluginResolver;

import java.util.LinkedList;
import java.util.List;

public class DefaultScriptPluginFactory implements ScriptPluginFactory {
    private final ScriptCompilerFactory scriptCompilerFactory;
    private final ImportsReader importsReader;
    private final Factory<LoggingManagerInternal> loggingManagerFactory;
    private final Instantiator instantiator;
    private final ScriptHandlerFactory scriptHandlerFactory;
    private final PluginResolverFactory pluginResolverFactory;
    private ClassLoader pluginParentClassLoader;

    public DefaultScriptPluginFactory(ScriptCompilerFactory scriptCompilerFactory,
                                      ImportsReader importsReader,
                                      Factory<LoggingManagerInternal> loggingManagerFactory,
                                      Instantiator instantiator,
                                      ScriptHandlerFactory scriptHandlerFactory,
                                      PluginResolverFactory pluginResolverFactory,
                                      ClassLoader pluginParentClassLoader) {
        this.scriptCompilerFactory = scriptCompilerFactory;
        this.importsReader = importsReader;
        this.loggingManagerFactory = loggingManagerFactory;
        this.instantiator = instantiator;
        this.scriptHandlerFactory = scriptHandlerFactory;
        this.pluginResolverFactory = pluginResolverFactory;
        this.pluginParentClassLoader = pluginParentClassLoader;
    }

    public ScriptPlugin create(ScriptSource scriptSource, ScriptHandlerInternal scriptHandler, String classpathClosureName, Class<? extends BasicScript> scriptClass) {
        return new ScriptPluginImpl(scriptSource, scriptHandler, classpathClosureName, scriptClass);
    }

    private class ScriptPluginImpl implements ScriptPlugin {
        private final ScriptSource scriptSource;
        private final String classpathClosureName;
        private final Class<? extends BasicScript> scriptType;
        private final ScriptHandlerInternal scriptHandler;

        public ScriptPluginImpl(ScriptSource scriptSource, ScriptHandlerInternal scriptHandler, String classpathClosureName, Class<? extends BasicScript> scriptType) {
            this.scriptSource = scriptSource;
            this.classpathClosureName = classpathClosureName;
            this.scriptHandler = scriptHandler;
            this.scriptType = scriptType;
        }

        public ScriptSource getSource() {
            return scriptSource;
        }

        public void apply(final Object target) {
            DefaultServiceRegistry services = new DefaultServiceRegistry();
            services.add(ScriptPluginFactory.class, DefaultScriptPluginFactory.this);
            services.add(ScriptHandlerFactory.class, scriptHandlerFactory);
            services.add(LoggingManagerInternal.class, loggingManagerFactory.create());
            services.add(Instantiator.class, instantiator);
            services.add(ScriptHandlerInternal.class, scriptHandler);

            ScriptAware scriptAware = null;
            if (target instanceof ScriptAware) {
                scriptAware = (ScriptAware) target;
                scriptAware.beforeCompile(this);
            }

            ScriptClassLoaderProvider classLoaderProvider = scriptHandler;
            ScriptSource withImports = importsReader.withImports(scriptSource);

            List<PluginRequest> pluginRequests = new LinkedList<PluginRequest>();
            if (target instanceof PluginAware) {
                services.add(PluginHandler.class, new DefaultPluginHandler(pluginRequests));
            } else {
                services.add(PluginHandler.class, new NonPluggableTargetPluginHandler(target));
            }

            ScriptCompiler compiler = scriptCompilerFactory.createCompiler(withImports);

            compiler.setClassloader(classLoaderProvider.getBaseCompilationClassLoader());


            PluginsAndBuildscriptTransformer scriptBlockTransformer = new PluginsAndBuildscriptTransformer(classpathClosureName);

            StatementExtractingScriptTransformer classpathScriptTransformer = new StatementExtractingScriptTransformer(classpathClosureName, scriptBlockTransformer);

            compiler.setTransformer(classpathScriptTransformer);

            ScriptRunner<? extends BasicScript> classPathScriptRunner = compiler.compile(scriptType);
            classPathScriptRunner.getScript().init(target, services);
            classPathScriptRunner.run();

            classLoaderProvider.updateClassPath();
            ClassLoader scriptCompileClassLoader = classLoaderProvider.getScriptCompileClassLoader();

            if (!pluginRequests.isEmpty()) {
                PluginResolver pluginResolver = pluginResolverFactory.createPluginResolver(scriptCompileClassLoader);
                MultiParentClassLoader pluginsClassLoader = new MultiParentClassLoader(scriptCompileClassLoader);
                @SuppressWarnings("ConstantConditions")
                PluginResolutionApplicator resolutionApplicator = new PluginResolutionApplicator((PluginAware) target, pluginParentClassLoader, pluginsClassLoader);
                PluginRequestApplicator requestApplicator = new PluginRequestApplicator(pluginResolver, resolutionApplicator);
                requestApplicator.applyPlugin(pluginRequests);
                scriptCompileClassLoader = pluginsClassLoader;
            }

            compiler.setClassloader(scriptCompileClassLoader);

            compiler.setTransformer(new BuildScriptTransformer("no_" + classpathScriptTransformer.getId(), classpathScriptTransformer.invert()));
            ScriptRunner<? extends BasicScript> runner = compiler.compile(scriptType);

            BasicScript script = runner.getScript();
            script.init(target, services);
            if (scriptAware != null) {
                scriptAware.afterCompile(this, script);
            }
            runner.run();
        }

    }
}