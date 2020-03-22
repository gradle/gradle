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

import org.gradle.api.Project;
import org.gradle.api.internal.initialization.ClassLoaderScope;
import org.gradle.api.internal.initialization.ScriptHandlerFactory;
import org.gradle.api.internal.initialization.ScriptHandlerInternal;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.configuration.CompileOperationFactory;
import org.gradle.configuration.DefaultScriptTarget;
import org.gradle.configuration.ScriptTarget;
import org.gradle.groovy.scripts.BasicScript;
import org.gradle.groovy.scripts.ScriptRunner;
import org.gradle.groovy.scripts.ScriptSource;
import org.gradle.groovy.scripts.TextResourceScriptSource;
import org.gradle.groovy.scripts.internal.BuildScriptData;
import org.gradle.groovy.scripts.internal.CompileOperation;
import org.gradle.groovy.scripts.internal.CompiledScript;
import org.gradle.groovy.scripts.internal.ScriptCompilationHandler;
import org.gradle.groovy.scripts.internal.ScriptRunnerFactory;
import org.gradle.internal.hash.HashCode;
import org.gradle.internal.resource.TextResource;
import org.gradle.internal.resource.UriTextResource;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.plugin.management.internal.PluginRequests;
import org.gradle.plugin.management.internal.autoapply.AutoAppliedPluginHandler;
import org.gradle.plugin.use.internal.PluginRequestApplicator;
import org.gradle.plugin.use.internal.PluginsAwareScript;

import java.io.File;

@SuppressWarnings("unused")
public class PreCompiledScriptRunner {

    private final Project project;
    private final ServiceRegistry projectServices;
    private final File pluginsBlockDir;
    private final File pluginsMetadataDir;
    private final File scriptClassesDir;
    private final File scriptMetadataDir;

    private final CompileOperationFactory compileOperationFactory;
    private final ScriptCompilationHandler scriptCompilationHandler;
    private final ScriptRunnerFactory scriptRunnerFactory;
    private final ScriptHandlerFactory scriptHandlerFactory;
    private final AutoAppliedPluginHandler autoAppliedPluginHandler;
    private final PluginRequestApplicator pluginRequestApplicator;

    private final ScriptSource scriptSource;
    private final HashCode hashCode;

    private final ClassLoaderScope classLoaderScope;
    private final ClassLoader classLoader;

    private final ScriptTarget scriptTarget;

    public PreCompiledScriptRunner(ProjectInternal project,
                                   String scriptFilePath,
                                   String baseClassesDir,
                                   String baseMetadataDir) {
        this.project = project;
        this.projectServices = project.getServices();

        TextResource scriptResource = new UriTextResource("script", new File(scriptFilePath));
        this.hashCode = scriptResource.getContentHash();
        this.scriptSource = new TextResourceScriptSource(scriptResource);
        PreCompiledScript script = new PreCompiledScript(scriptSource);
        this.pluginsBlockDir = new File(baseClassesDir, script.getPluginClassesDirPath());
        this.pluginsMetadataDir = new File(baseMetadataDir, script.getPluginMetadataDirPath());
        this.scriptClassesDir = new File(baseClassesDir, script.getBuildScriptClassesDirPath());
        this.scriptMetadataDir = new File(baseMetadataDir, script.getBuildScriptMetadataDirPath());

        this.compileOperationFactory = projectServices.get(CompileOperationFactory.class);
        this.scriptCompilationHandler = projectServices.get(ScriptCompilationHandler.class);
        this.scriptRunnerFactory = projectServices.get(ScriptRunnerFactory.class);
        this.scriptHandlerFactory = projectServices.get(ScriptHandlerFactory.class);
        this.autoAppliedPluginHandler = projectServices.get(AutoAppliedPluginHandler.class);
        this.pluginRequestApplicator = projectServices.get(PluginRequestApplicator.class);

        // TODO is this the right scope?
        this.classLoaderScope = project.getClassLoaderScope().createChild("pre-compiled-script");
        this.classLoaderScope.lock();
        this.classLoader = classLoaderScope.getExportClassLoader();

        this.scriptTarget = new DefaultScriptTarget(project);
    }

    public void run() {
        applyPlugins(extractPlugins());
        executeScript();
    }

    private PluginRequests extractPlugins() {
        CompileOperation<?> pluginRequestsCompileOperation = compileOperationFactory.getPluginRequestsCompileOperation(scriptTarget);
        CompiledScript<? extends BasicScript, ?> compiledPluginRequests = loadCompiledScript(pluginsBlockDir, pluginsMetadataDir, pluginRequestsCompileOperation);
        ScriptRunner<? extends BasicScript, ?> initialRunner = scriptRunnerFactory.create(compiledPluginRequests, scriptSource, classLoader);
        initialRunner.run(project, projectServices);
        PluginRequests initialPluginRequests = getInitialPluginRequests(initialRunner);
        return autoAppliedPluginHandler.mergeWithAutoAppliedPlugins(initialPluginRequests, project);
    }

    private void applyPlugins(PluginRequests pluginRequests) {
        ScriptHandlerInternal scriptHandler = scriptHandlerFactory.create(scriptSource, classLoaderScope);
        pluginRequestApplicator.applyPlugins(pluginRequests, scriptHandler, scriptTarget.getPluginManager(), classLoaderScope);
    }

    private void executeScript() {
        CompileOperation<BuildScriptData> buildScriptDataCompileOperation = compileOperationFactory.getBuildScriptDataCompileOperation(scriptSource, scriptTarget);
        CompiledScript<? extends BasicScript, BuildScriptData> compiledScript = loadCompiledScript(scriptClassesDir, scriptMetadataDir, buildScriptDataCompileOperation);
        ScriptRunner<? extends BasicScript, BuildScriptData> runner = scriptRunnerFactory.create(compiledScript, scriptSource, classLoader);
        if (runner.getRunDoesSomething()) {
            runner.run(project, projectServices);
        }
    }

    private <T> CompiledScript<? extends BasicScript, T> loadCompiledScript(File classesDir, File metadataDir, CompileOperation<T> compileOperation) {
        return scriptCompilationHandler.loadFromDir(
            scriptSource, hashCode, classLoaderScope, classesDir, metadataDir,
            compileOperation, scriptTarget.getScriptClass());
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
