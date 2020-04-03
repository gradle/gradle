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
import org.gradle.api.Project;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.Directory;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.FileSystemOperations;
import org.gradle.api.internal.initialization.ClassLoaderScope;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Classpath;
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
import org.gradle.internal.classpath.DefaultClassPath;
import org.gradle.model.dsl.internal.transform.ClosureCreationInterceptingVerifier;

import javax.inject.Inject;
import java.io.File;
import java.net.URLClassLoader;
import java.util.Set;
import java.util.stream.Collectors;

@CacheableTask
abstract class CompileGroovyScriptPluginsTask extends DefaultTask {

    private final ScriptCompilationHandler scriptCompilationHandler;
    private final CompileOperationFactory compileOperationFactory;
    private final FileSystemOperations fileSystemOperations;
    private final ClassLoaderScope classLoaderScope;

    private final Provider<Directory> intermediatePluginClassesDir;
    private final Provider<Directory> intermediatePluginMetadataDir;

    @Inject
    public CompileGroovyScriptPluginsTask(ScriptCompilationHandler scriptCompilationHandler,
                                          ClassLoaderScopeRegistry classLoaderScopeRegistry,
                                          CompileOperationFactory compileOperationFactory,
                                          FileSystemOperations fileSystemOperations) {
        this.scriptCompilationHandler = scriptCompilationHandler;
        this.compileOperationFactory = compileOperationFactory;
        this.fileSystemOperations = fileSystemOperations;
        this.classLoaderScope = classLoaderScopeRegistry.getCoreAndPluginsScope();

        Project project = getProject();
        DirectoryProperty buildDir = project.getLayout().getBuildDirectory();
        this.intermediatePluginClassesDir = buildDir.dir("groovy-dsl-plugins/work/classes");
        this.intermediatePluginMetadataDir = buildDir.dir("groovy-dsl-plugins/work/metadata");
    }

    @PathSensitive(PathSensitivity.RELATIVE)
    @InputFiles
    Set<File> getScriptFiles() {
        return getScriptPlugins().get().stream().map(p -> p.getSource().getResource().getFile()).collect(Collectors.toSet());
    }

    @Classpath
    abstract ConfigurableFileCollection getClasspath();

    @OutputDirectory
    abstract DirectoryProperty getPrecompiledGroovyScriptsOutputDir();

    @Internal
    abstract ListProperty<PrecompiledGroovyScript> getScriptPlugins();

    @TaskAction
    void compileScripts() {
        ClassLoader compileClassLoader = new URLClassLoader(DefaultClassPath.of(getClasspath()).getAsURLArray(), classLoaderScope.getLocalClassLoader());

        for (PrecompiledGroovyScript scriptPlugin : getScriptPlugins().get()) {
            compileBuildScript(scriptPlugin, compileClassLoader);
        }

        fileSystemOperations.copy(copySpec -> {
            copySpec.from(intermediatePluginClassesDir.get().getAsFileTree().getFiles());
            copySpec.into(getPrecompiledGroovyScriptsOutputDir());
        });
    }

    private void compileBuildScript(PrecompiledGroovyScript scriptPlugin, ClassLoader compileClassLoader) {
        ScriptTarget target = scriptPlugin.getScriptTarget();
        CompileOperation<BuildScriptData> scriptCompileOperation = compileOperationFactory.getScriptCompileOperation(scriptPlugin.getSource(), target);
        File scriptMetadataDir = subdirectory(intermediatePluginMetadataDir, scriptPlugin.getId());
        File scriptClassesDir = subdirectory(intermediatePluginClassesDir, scriptPlugin.getId());
        scriptCompilationHandler.compileToDir(
            scriptPlugin.getSource(), compileClassLoader, scriptClassesDir,
            scriptMetadataDir, scriptCompileOperation, target.getScriptClass(),
            ClosureCreationInterceptingVerifier.INSTANCE);
    }

    private static File subdirectory(Provider<Directory> root, String subdirPath) {
        return root.get().dir(subdirPath).getAsFile();
    }

}
