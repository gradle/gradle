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
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.FileSystemOperations;
import org.gradle.api.internal.initialization.ClassLoaderScope;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.IgnoreEmptyDirectories;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.SkipWhenEmpty;
import org.gradle.api.tasks.TaskAction;
import org.gradle.configuration.CompileOperationFactory;
import org.gradle.groovy.scripts.internal.CompileOperation;
import org.gradle.groovy.scripts.internal.ScriptCompilationHandler;
import org.gradle.initialization.ClassLoaderScopeRegistry;
import org.gradle.internal.Actions;

import javax.inject.Inject;
import java.io.File;

@CacheableTask
public abstract class ExtractPluginRequestsTask extends DefaultTask {
    @Inject
    abstract protected FileSystemOperations getFileSystemOperations();

    @Inject
    abstract protected ClassLoaderScopeRegistry getClassLoaderScopeRegistry();

    @Inject
    abstract protected ScriptCompilationHandler getScriptCompilationHandler();

    @Inject
    abstract protected CompileOperationFactory getCompileOperationFactory();

    @InputFiles
    @SkipWhenEmpty
    @IgnoreEmptyDirectories
    @PathSensitive(PathSensitivity.RELATIVE)
    abstract ConfigurableFileCollection getScriptFiles();

    @OutputDirectory
    abstract DirectoryProperty getExtractedPluginRequestsClassesDirectory();

    @OutputDirectory
    abstract DirectoryProperty getExtractedPluginRequestsClassesStagingDirectory();

    @Internal
    abstract ListProperty<PrecompiledGroovyScript> getScriptPlugins();

    @TaskAction
    void extractPluginsBlocks() {
        getFileSystemOperations().delete(spec -> spec.delete(getExtractedPluginRequestsClassesDirectory()));
        getExtractedPluginRequestsClassesDirectory().get().getAsFile().mkdirs();

        // TODO: Use worker API?
        for (PrecompiledGroovyScript scriptPlugin : getScriptPlugins().get()) {
            compilePluginsBlock(scriptPlugin);
        }
    }

    private void compilePluginsBlock(PrecompiledGroovyScript scriptPlugin) {
        ClassLoaderScope classLoaderScope = getClassLoaderScopeRegistry().getCoreAndPluginsScope();
        CompileOperation<?> pluginsCompileOperation = getCompileOperationFactory().getPluginsBlockCompileOperation(scriptPlugin.getScriptTarget());
        File outputDir = getExtractedPluginRequestsClassesDirectory().get().dir(scriptPlugin.getId()).getAsFile();
        getScriptCompilationHandler().compileToDir(
            scriptPlugin.getFirstPassSource(), classLoaderScope.getExportClassLoader(), outputDir, outputDir, pluginsCompileOperation,
            FirstPassPrecompiledScript.class, Actions.doNothing());

        getFileSystemOperations().sync(copySpec -> {
            copySpec.from(getExtractedPluginRequestsClassesDirectory().getAsFileTree().getFiles()).include("**.class");
            copySpec.into(getExtractedPluginRequestsClassesStagingDirectory());
        });
    }

}
