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

import org.gradle.api.DefaultTask;
import org.gradle.api.file.CopySpec;
import org.gradle.api.file.Directory;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.internal.initialization.ClassLoaderScope;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;
import org.gradle.configuration.CompileOperationFactory;
import org.gradle.configuration.ScriptTarget;
import org.gradle.groovy.scripts.internal.BuildScriptData;
import org.gradle.groovy.scripts.internal.CompileOperation;
import org.gradle.groovy.scripts.internal.ScriptCompilationHandler;
import org.gradle.initialization.ClassLoaderScopeRegistry;
import org.gradle.internal.Actions;
import org.gradle.model.dsl.internal.transform.ClosureCreationInterceptingVerifier;

import javax.inject.Inject;
import java.io.File;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

@CacheableTask
class PreCompileGroovyScriptsTask extends DefaultTask {

    private final ScriptCompilationHandler scriptCompilationHandler;
    private final ClassLoaderScopeRegistry classLoaderScopeRegistry;
    private final CompileOperationFactory compileOperationFactory;

    private final Set<PreCompiledScript> scriptPlugins = new HashSet<>();
    private final DirectoryProperty classesDir = getProject().getObjects().directoryProperty();
    private final DirectoryProperty metadataDir = getProject().getObjects().directoryProperty();

    private final CopySpec classesSpec = getProject().copySpec();

    @Inject
    public PreCompileGroovyScriptsTask(ScriptCompilationHandler scriptCompilationHandler,
                                       ClassLoaderScopeRegistry classLoaderScopeRegistry,
                                       CompileOperationFactory compileOperationFactory) {
        this.scriptCompilationHandler = scriptCompilationHandler;
        this.classLoaderScopeRegistry = classLoaderScopeRegistry;
        this.compileOperationFactory = compileOperationFactory;
    }

    @Internal
    Set<PreCompiledScript> getScriptPlugins() {
        return scriptPlugins;
    }

    @PathSensitive(PathSensitivity.RELATIVE)
    @InputFiles
    Set<File> getScriptFiles() {
        return scriptPlugins.stream().map(PreCompiledScript::getScriptFile).collect(Collectors.toSet());
    }

    @Internal
    DirectoryProperty getClassesDir() {
        return classesDir;
    }

    @OutputDirectory
    Provider<Directory> getClassOutputDir() {
        return classesDir.dir("classes");
    }

    @OutputDirectory
    DirectoryProperty getMetadataDir() {
        return metadataDir;
    }

    @TaskAction
    void compileScripts() {
        ClassLoaderScope classLoaderScope = classLoaderScopeRegistry.getCoreAndPluginsScope().createChild("pre-compiled-scripts");
        classLoaderScope.lock();
        ClassLoader classLoader = classLoaderScope.getExportClassLoader();

        for (PreCompiledScript scriptPlugin : scriptPlugins) {
            compilePluginRequests(classLoader, scriptPlugin);
            compileBuildScript(classLoader, scriptPlugin);
        }

        getProject().copy(copySpec -> {
            copySpec.with(classesSpec);
            copySpec.into(getClassOutputDir());
        });
    }

    private void compilePluginRequests(ClassLoader classLoader, PreCompiledScript scriptPlugin) {
        ScriptTarget target = scriptPlugin.getScriptTarget();
        CompileOperation<?> pluginRequestsCompileOperation = compileOperationFactory.getPluginRequestsCompileOperation(target);
        File pluginMetadataDir = new File(metadataDir.getAsFile().get(), scriptPlugin.getPluginMetadataDirPath());
        File pluginClassesDir = new File(classesDir.getAsFile().get(), scriptPlugin.getPluginClassesDirPath());
        scriptCompilationHandler.compileToDir(
            scriptPlugin.getPluginsBlockSource(), classLoader, pluginClassesDir, pluginMetadataDir, pluginRequestsCompileOperation,
            target.getScriptClass(), Actions.doNothing());

        classesSpec.from(pluginClassesDir);
    }

    private void compileBuildScript(ClassLoader classLoader, PreCompiledScript scriptPlugin) {
        ScriptTarget target = scriptPlugin.getScriptTarget();
        CompileOperation<BuildScriptData> buildScriptDataCompileOperation = compileOperationFactory.getBuildScriptDataCompileOperation(
            scriptPlugin.getSource(), target);
        File buildScriptMetadataDir = new File(metadataDir.getAsFile().get(), scriptPlugin.getBuildScriptMetadataDirPath());
        File buildScriptClassesDir = new File(classesDir.getAsFile().get(), scriptPlugin.getBuildScriptClassesDirPath());
        scriptCompilationHandler.compileToDir(
            scriptPlugin.getSource(), classLoader, buildScriptClassesDir,
            buildScriptMetadataDir, buildScriptDataCompileOperation, target.getScriptClass(),
            ClosureCreationInterceptingVerifier.INSTANCE);

        classesSpec.from(buildScriptClassesDir);
    }
}
