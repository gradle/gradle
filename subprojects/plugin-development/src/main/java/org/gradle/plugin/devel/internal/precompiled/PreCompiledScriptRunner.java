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

import groovy.lang.Script;
import org.gradle.api.Project;
import org.gradle.api.initialization.Settings;
import org.gradle.api.internal.GradleInternal;
import org.gradle.api.internal.SettingsInternal;
import org.gradle.api.internal.initialization.ClassLoaderScope;
import org.gradle.api.internal.initialization.ScriptHandlerFactory;
import org.gradle.api.internal.initialization.ScriptHandlerInternal;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.invocation.Gradle;
import org.gradle.configuration.DefaultScriptTarget;
import org.gradle.configuration.ScriptTarget;
import org.gradle.groovy.scripts.BasicScript;
import org.gradle.groovy.scripts.ScriptRunner;
import org.gradle.groovy.scripts.ScriptSource;
import org.gradle.groovy.scripts.internal.BuildScriptData;
import org.gradle.groovy.scripts.internal.CompiledScript;
import org.gradle.groovy.scripts.internal.ScriptRunnerFactory;
import org.gradle.internal.resource.StringTextResource;
import org.gradle.internal.resource.TextResource;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.plugin.use.internal.PluginRequestApplicator;
import org.gradle.plugin.use.internal.PluginsAwareScript;

import javax.annotation.Nullable;

@SuppressWarnings("unused")
public class PreCompiledScriptRunner {

    private final Object target;
    private final ServiceRegistry serviceRegistry;

    private final ScriptRunnerFactory scriptRunnerFactory;
    private final ScriptHandlerFactory scriptHandlerFactory;
    private final PluginRequestApplicator pluginRequestApplicator;

    private final ClassLoaderScope classLoaderScope;
    private final ClassLoader classLoader;

    private final ScriptTarget scriptTarget;

    public PreCompiledScriptRunner(Project project) {
        this(project, ((ProjectInternal) project).getServices(), ((ProjectInternal) project).getClassLoaderScope());
    }

    public PreCompiledScriptRunner(Settings settings) {
        this(settings, ((SettingsInternal) settings).getGradle().getServices(), ((SettingsInternal) settings).getClassLoaderScope());
    }

    public PreCompiledScriptRunner(Gradle gradle) {
        this(gradle, ((GradleInternal) gradle).getServices(), ((GradleInternal) gradle).getClassLoaderScope().createChild("init-plugin"));
    }

    private PreCompiledScriptRunner(Object target, ServiceRegistry serviceRegistry, ClassLoaderScope classLoaderScope) {
        this.target = target;
        this.serviceRegistry = serviceRegistry;

        this.scriptRunnerFactory = serviceRegistry.get(ScriptRunnerFactory.class);
        this.scriptHandlerFactory = serviceRegistry.get(ScriptHandlerFactory.class);
        this.pluginRequestApplicator = serviceRegistry.get(PluginRequestApplicator.class);

        this.classLoaderScope = classLoaderScope;
        this.classLoaderScope.lock();
        this.classLoader = classLoaderScope.getExportClassLoader();

        this.scriptTarget = new DefaultScriptTarget(target);
    }

    public void run(Class<?> pluginsBlockClass,
                    Class<?> precompiledScriptClass,
                    boolean scriptRunDoesSomething,
                    boolean scriptHasMethods,
                    boolean scriptHasImperativeStatements) {
        ScriptSource scriptSource = new PrecompiledScriptSource(precompiledScriptClass);

        if (pluginsBlockClass != null) {
            CompiledScript<PluginsAwareScript, BuildScriptData> compiledPlugins = new CompiledGroovyPlugin<>(pluginsBlockClass, PluginsAwareScript.class, classLoaderScope, true, false, false);
            ScriptRunner<PluginsAwareScript, BuildScriptData> runner = scriptRunnerFactory.create(compiledPlugins, scriptSource, classLoader);
            runner.run(target, serviceRegistry);

            ScriptHandlerInternal scriptHandler = scriptHandlerFactory.create(scriptSource, classLoaderScope);
            pluginRequestApplicator.applyPlugins(runner.getScript().getPluginRequests(), scriptHandler, scriptTarget.getPluginManager(), classLoaderScope);
        }

        CompiledScript<BasicScript, BuildScriptData> compiledScript =
            new CompiledGroovyPlugin<>(precompiledScriptClass, BasicScript.class, classLoaderScope, scriptRunDoesSomething, scriptHasMethods, scriptHasImperativeStatements);
        ScriptRunner<? extends BasicScript, BuildScriptData> runner = scriptRunnerFactory.create(compiledScript, scriptSource, classLoader);
        runner.run(target, serviceRegistry);
    }

    private static class CompiledGroovyPlugin<T extends Script> implements CompiledScript<T, BuildScriptData> {

        private final Class<? extends T> compiledClass;
        private final ClassLoaderScope scope;
        private final boolean runDoesSomething;
        private final boolean hasMethods;
        private final boolean hasImperativeStatements;

        private CompiledGroovyPlugin(Class<?> scriptClass, Class<T> scriptBaseClass, ClassLoaderScope scope,
                                     boolean runDoesSomething, boolean hasMethods, boolean hasImperativeStatements) {
            this.compiledClass = scriptClass.asSubclass(scriptBaseClass);
            this.scope = scope;
            this.runDoesSomething = runDoesSomething;
            this.hasMethods = hasMethods;
            this.hasImperativeStatements = hasImperativeStatements;
        }

        @Override
        public boolean getRunDoesSomething() {
            return runDoesSomething;
        }

        @Override
        public boolean getHasMethods() {
            return hasMethods;
        }

        @Override
        public Class<? extends T> loadClass() {
            return compiledClass;
        }

        @Override
        public BuildScriptData getData() {
            return new BuildScriptData(hasImperativeStatements);
        }

        @Override
        public void onReuse() {
            scope.onReuse();
        }
    }

    private static class PrecompiledScriptSource implements ScriptSource {

        private final Class<?> scriptClass;

        private PrecompiledScriptSource(Class<?> scriptClass) {
            this.scriptClass = scriptClass;
        }

        @Override
        public String getClassName() {
            return scriptClass.getSimpleName();
        }

        @Override
        public TextResource getResource() {
            return new StringTextResource(scriptClass.getSimpleName(), "");
        }

        @Nullable
        @Override
        public String getFileName() {
            return null;
        }

        @Override
        public String getDisplayName() {
            return scriptClass.getSimpleName();
        }
    }
}
