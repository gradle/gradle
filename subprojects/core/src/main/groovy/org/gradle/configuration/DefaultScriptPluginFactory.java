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

import com.google.common.collect.ListMultimap;
import org.gradle.api.Transformer;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.initialization.dsl.ScriptHandler;
import org.gradle.api.internal.DocumentationRegistry;
import org.gradle.api.internal.file.FileLookup;
import org.gradle.api.internal.initialization.ClassLoaderScope;
import org.gradle.api.internal.initialization.ScriptHandlerFactory;
import org.gradle.api.internal.project.ProjectScript;
import org.gradle.api.plugins.PluginAware;
import org.gradle.groovy.scripts.*;
import org.gradle.groovy.scripts.internal.BuildScriptTransformer;
import org.gradle.groovy.scripts.internal.PluginsAndBuildscriptTransformer;
import org.gradle.groovy.scripts.internal.StatementExtractingScriptTransformer;
import org.gradle.internal.Factory;
import org.gradle.internal.classpath.ClassPath;
import org.gradle.internal.classpath.DefaultClassPath;
import org.gradle.internal.exceptions.LocationAwareException;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.service.DefaultServiceRegistry;
import org.gradle.logging.LoggingManagerInternal;
import org.gradle.plugin.internal.PluginId;
import org.gradle.plugin.use.internal.InvalidPluginRequestException;
import org.gradle.plugin.use.internal.PluginDependenciesService;
import org.gradle.plugin.use.internal.PluginRequest;
import org.gradle.plugin.use.internal.PluginRequestApplicator;
import org.gradle.util.CollectionUtils;

import java.io.File;
import java.util.List;
import java.util.Set;

public class DefaultScriptPluginFactory implements ScriptPluginFactory {

    public static final PluginId NOOP_PLUGIN_ID = PluginId.of("noop");

    private final ScriptCompilerFactory scriptCompilerFactory;
    private final ImportsReader importsReader;
    private final Factory<LoggingManagerInternal> loggingManagerFactory;
    private final Instantiator instantiator;
    private final ScriptHandlerFactory scriptHandlerFactory;
    private final PluginRequestApplicator pluginRequestApplicator;
    private final FileLookup fileLookup;
    private final DocumentationRegistry documentationRegistry;

    public DefaultScriptPluginFactory(ScriptCompilerFactory scriptCompilerFactory,
                                      ImportsReader importsReader,
                                      Factory<LoggingManagerInternal> loggingManagerFactory,
                                      Instantiator instantiator,
                                      ScriptHandlerFactory scriptHandlerFactory,
                                      PluginRequestApplicator pluginRequestApplicator,
                                      FileLookup fileLookup,
                                      DocumentationRegistry documentationRegistry) {
        this.scriptCompilerFactory = scriptCompilerFactory;
        this.importsReader = importsReader;
        this.loggingManagerFactory = loggingManagerFactory;
        this.instantiator = instantiator;
        this.scriptHandlerFactory = scriptHandlerFactory;
        this.pluginRequestApplicator = pluginRequestApplicator;
        this.fileLookup = fileLookup;
        this.documentationRegistry = documentationRegistry;
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
            DefaultServiceRegistry services = new DefaultServiceRegistry();
            services.add(ScriptPluginFactory.class, DefaultScriptPluginFactory.this);
            services.add(ScriptHandlerFactory.class, scriptHandlerFactory);
            services.add(ClassLoaderScope.class, targetScope);
            services.add(LoggingManagerInternal.class, loggingManagerFactory.create());
            services.add(Instantiator.class, instantiator);
            services.add(ScriptHandler.class, scriptHandler);
            services.add(FileLookup.class, fileLookup);

            ScriptSource withImports = importsReader.withImports(scriptSource);

            PluginDependenciesService pluginDependenciesService = new PluginDependenciesService(getSource());
            services.add(PluginDependenciesService.class, pluginDependenciesService);

            ScriptCompiler compiler = scriptCompilerFactory.createCompiler(withImports);
            compiler.setClassloader(baseScope.getExportClassLoader());

            boolean supportsPluginsBlock = ProjectScript.class.isAssignableFrom(scriptType);
            String onPluginBlockError = supportsPluginsBlock ? null : "Only Project build scripts can contain plugins {} blocks";

            PluginsAndBuildscriptTransformer scriptBlockTransformer = new PluginsAndBuildscriptTransformer(classpathClosureName, onPluginBlockError, documentationRegistry);

            StatementExtractingScriptTransformer classpathScriptTransformer = new StatementExtractingScriptTransformer(classpathClosureName, scriptBlockTransformer);

            compiler.setTransformer(classpathScriptTransformer);

            ScriptRunner<? extends BasicScript> classPathScriptRunner = compiler.compile(scriptType);
            classPathScriptRunner.getScript().init(target, services);
            classPathScriptRunner.run();

            List<PluginRequest> pluginRequests = pluginDependenciesService.getRequests();
            if (!pluginRequests.isEmpty()) { // implies target is PluginAware
                ListMultimap<PluginId, PluginRequest> groupedById = CollectionUtils.groupBy(pluginRequests, new Transformer<PluginId, PluginRequest>() {
                    public PluginId transform(PluginRequest pluginRequest) {
                        return pluginRequest.getId();
                    }
                });

                // Check for duplicates
                for (PluginId key : groupedById.keySet()) {
                    // Ignore duplicate noops - noop plugin just used for testing
                    if (key.equals(NOOP_PLUGIN_ID)) {
                        continue;
                    }

                    List<PluginRequest> pluginRequestsForId = groupedById.get(key);
                    if (pluginRequestsForId.size() > 1) {
                        PluginRequest first = pluginRequests.get(0);
                        PluginRequest second = pluginRequests.get(1);

                        InvalidPluginRequestException exception = new InvalidPluginRequestException(second, "Plugin with id '" + key + "' was already requested at line " + first.getLineNumber());
                        throw new LocationAwareException(exception, second.getScriptSource(), second.getLineNumber());
                    }
                }

                // This is safe because earlier on we only allow plugins {} for ProjectScript
                PluginAware pluginAware = (PluginAware) target;
                pluginRequestApplicator.applyPlugins(pluginRequests, scriptHandler, pluginAware, targetScope);
            } else {
                Configuration classpathConfiguration = scriptHandler.getConfigurations().getByName(ScriptHandler.CLASSPATH_CONFIGURATION);
                Set<File> files = classpathConfiguration.getFiles();
                if (!files.isEmpty()) {
                    ClassPath classPath = new DefaultClassPath(files);
                    Factory<? extends ClassLoader> loader = targetScope.getParent().loader(classPath);
                    targetScope.export(loader);
                }
                targetScope.lock();
            }

            compiler.setClassloader(targetScope.getLocalClassLoader());

            compiler.setTransformer(new BuildScriptTransformer("no_" + classpathScriptTransformer.getId(), classpathScriptTransformer.invert()));
            ScriptRunner<? extends BasicScript> runner = compiler.compile(scriptType);

            BasicScript script = runner.getScript();
            script.init(target, services);
            if (ownerScript && target instanceof ScriptAware) {
                ((ScriptAware) target).setScript(script);
            }
            runner.run();
        }

    }
}