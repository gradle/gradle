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
import org.gradle.groovy.scripts.*;
import org.gradle.groovy.scripts.internal.BuildScriptClasspathScriptTransformer;
import org.gradle.groovy.scripts.internal.BuildScriptTransformer;
import org.gradle.internal.Factory;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.service.DefaultServiceRegistry;
import org.gradle.logging.LoggingManagerInternal;

public class DefaultScriptPluginFactory implements ScriptPluginFactory {
    private final ScriptCompilerFactory scriptCompilerFactory;
    private final ImportsReader importsReader;
    private final ScriptHandlerFactory scriptHandlerFactory;
    private final ClassLoader defaultClassLoader;
    private final Factory<LoggingManagerInternal> loggingManagerFactory;
    private final Instantiator instantiator;

    public DefaultScriptPluginFactory(ScriptCompilerFactory scriptCompilerFactory,
                                      ImportsReader importsReader,
                                      ScriptHandlerFactory scriptHandlerFactory,
                                      ClassLoader defaultClassLoader,
                                      Factory<LoggingManagerInternal> loggingManagerFactory, Instantiator instantiator) {
        this.scriptCompilerFactory = scriptCompilerFactory;
        this.importsReader = importsReader;
        this.scriptHandlerFactory = scriptHandlerFactory;
        this.defaultClassLoader = defaultClassLoader;
        this.loggingManagerFactory = loggingManagerFactory;
        this.instantiator = instantiator;
    }

    public ScriptPlugin create(ScriptSource scriptSource) {
        return new ScriptPluginImpl(scriptSource);
    }

    private class ScriptPluginImpl implements ScriptPlugin {
        private final ScriptSource scriptSource;
        private String classpathClosureName = "buildscript";
        private Class<? extends BasicScript> scriptType = DefaultScript.class;
        private ScriptClassLoaderProvider classLoaderProvider;
        private ClassLoader classLoader = defaultClassLoader;

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

        public ScriptPlugin setClassLoader(ClassLoader classLoader) {
            this.classLoader = classLoader;
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

        public void apply(Object target) {
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
                ScriptHandlerInternal defaultScriptHandler = scriptHandlerFactory.create(withImports, classLoader);
                services.add(ScriptHandlerInternal.class, defaultScriptHandler);
                classLoaderProvider = defaultScriptHandler;
            }
            
            ScriptCompiler compiler = scriptCompilerFactory.createCompiler(withImports);

            compiler.setClassloader(classLoaderProvider.getClassLoader());

            BuildScriptClasspathScriptTransformer classpathScriptTransformer
                    = new BuildScriptClasspathScriptTransformer(classpathClosureName);
            compiler.setTransformer(classpathScriptTransformer);

            ScriptRunner<? extends BasicScript> classPathScriptRunner = compiler.compile(scriptType);
            classPathScriptRunner.getScript().init(target, services);
            classPathScriptRunner.run();

            classLoaderProvider.updateClassPath();

            compiler.setTransformer(new BuildScriptTransformer(classpathScriptTransformer));
            ScriptRunner<? extends BasicScript> runner = compiler.compile(scriptType);

            runner.getScript().init(target, services);
            if (scriptAware != null) {
                scriptAware.afterCompile(this, runner.getScript());
            }
            runner.run();
        }
    }
}