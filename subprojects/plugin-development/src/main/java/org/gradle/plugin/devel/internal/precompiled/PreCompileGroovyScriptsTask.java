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
import org.gradle.api.UncheckedIOException;
import org.gradle.api.file.Directory;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.internal.initialization.ClassLoaderScope;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;
import org.gradle.configuration.CompileOperationFactory;
import org.gradle.configuration.ScriptTarget;
import org.gradle.groovy.scripts.BasicScript;
import org.gradle.groovy.scripts.internal.BuildScriptData;
import org.gradle.groovy.scripts.internal.CompileOperation;
import org.gradle.groovy.scripts.internal.CompiledScript;
import org.gradle.groovy.scripts.internal.ScriptCompilationHandler;
import org.gradle.initialization.ClassLoaderScopeRegistry;
import org.gradle.internal.Actions;
import org.gradle.model.dsl.internal.transform.ClosureCreationInterceptingVerifier;

import javax.inject.Inject;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Set;

@CacheableTask
class PreCompileGroovyScriptsTask extends DefaultTask {

    private final ScriptCompilationHandler scriptCompilationHandler;
    private final ClassLoaderScopeRegistry classLoaderScopeRegistry;
    private final CompileOperationFactory compileOperationFactory;

    private final Set<File> pluginSourceFiles;
    private final List<PreCompiledGroovyScript> scriptPlugins;

    private final DirectoryProperty classesDir = getProject().getObjects().directoryProperty();
    private final DirectoryProperty metadataDir = getProject().getObjects().directoryProperty();

    private final DirectoryProperty precompiledGroovyScriptsDir = getProject().getObjects().directoryProperty();
    private final DirectoryProperty generatedPluginAdaptersDir = getProject().getObjects().directoryProperty();

    @Inject
    public PreCompileGroovyScriptsTask(ScriptCompilationHandler scriptCompilationHandler,
                                       ClassLoaderScopeRegistry classLoaderScopeRegistry,
                                       CompileOperationFactory compileOperationFactory,
                                       Set<File> pluginSourceFiles,
                                       List<PreCompiledGroovyScript> scriptPlugins) {
        this.scriptCompilationHandler = scriptCompilationHandler;
        this.classLoaderScopeRegistry = classLoaderScopeRegistry;
        this.compileOperationFactory = compileOperationFactory;
        this.pluginSourceFiles = pluginSourceFiles;
        this.scriptPlugins = scriptPlugins;

        DirectoryProperty buildDir = getProject().getLayout().getBuildDirectory();
        this.classesDir.set(buildDir.dir("groovy-dsl/compiled-scripts/classes"));
        this.metadataDir.set(buildDir.dir("groovy-dsl/compiled-scripts/metadata"));

        this.precompiledGroovyScriptsDir.set(buildDir.dir("generated-classes/groovy-dsl-plugins/classes"));
        this.generatedPluginAdaptersDir.set(buildDir.dir("generated-classes/groovy-dsl-plugins/java"));
    }

    @PathSensitive(PathSensitivity.RELATIVE)
    @InputFiles
    Set<File> getScriptFiles() {
        return pluginSourceFiles;
    }

    @OutputDirectory
    Provider<Directory> getPrecompiledGroovyScriptsDir() {
        return precompiledGroovyScriptsDir;
    }

    @OutputDirectory
    DirectoryProperty getGeneratedPluginAdaptersDir() {
        return generatedPluginAdaptersDir;
    }

    @TaskAction
    void compileScripts() {
        ClassLoaderScope classLoaderScope = classLoaderScopeRegistry.getCoreAndPluginsScope().createChild("pre-compiled-scripts");
        classLoaderScope.lock();

        for (PreCompiledGroovyScript scriptPlugin : scriptPlugins) {
            CompiledScript<? extends BasicScript, ?> pluginsBlock = compilePluginsBlock(classLoaderScope, scriptPlugin);
            CompiledScript<? extends BasicScript, ?> buildScript = compileBuildScript(classLoaderScope, scriptPlugin);
            generateScriptPluginAdapter(scriptPlugin, pluginsBlock, buildScript);
        }

        getProject().copy(copySpec -> {
            copySpec.from(metadataDir.getAsFile(), classesDir.getAsFileTree().getFiles());
            copySpec.into(precompiledGroovyScriptsDir);
        });
    }

    private CompiledScript<? extends BasicScript, ?> compilePluginsBlock(ClassLoaderScope classLoaderScope, PreCompiledGroovyScript scriptPlugin) {
        ScriptTarget target = scriptPlugin.getScriptTarget();
        CompileOperation<?> pluginsCompileOperation = compileOperationFactory.getPluginsBlockCompileOperation(target);
        String targetPath = scriptPlugin.getPluginsBlockClassName();
        File pluginsMetadataDir = subdirectory(metadataDir, targetPath);
        File pluginsClassesDir = subdirectory(classesDir, targetPath);
        scriptCompilationHandler.compileToDir(
            scriptPlugin.getPluginsBlockSource(), classLoaderScope.getExportClassLoader(), pluginsClassesDir, pluginsMetadataDir, pluginsCompileOperation,
            target.getScriptClass(), Actions.doNothing());

        return scriptCompilationHandler.loadFromDir(scriptPlugin.getPluginsBlockSource(), scriptPlugin.getContentHash(),
            classLoaderScope, pluginsClassesDir, pluginsMetadataDir, pluginsCompileOperation, target.getScriptClass());
    }

    private CompiledScript<? extends BasicScript, ?> compileBuildScript(ClassLoaderScope classLoaderScope, PreCompiledGroovyScript scriptPlugin) {
        ScriptTarget target = scriptPlugin.getScriptTarget();
        CompileOperation<BuildScriptData> scriptCompileOperation = compileOperationFactory.getScriptCompileOperation(scriptPlugin.getSource(), target);
        String targetPath = scriptPlugin.getClassName();
        File scriptMetadataDir = subdirectory(metadataDir, targetPath);
        File scriptClassesDir = subdirectory(classesDir, targetPath);
        scriptCompilationHandler.compileToDir(
            scriptPlugin.getSource(), classLoaderScope.getExportClassLoader(), scriptClassesDir,
            scriptMetadataDir, scriptCompileOperation, target.getScriptClass(),
            ClosureCreationInterceptingVerifier.INSTANCE);

        return scriptCompilationHandler.loadFromDir(scriptPlugin.getSource(), scriptPlugin.getContentHash(),
            classLoaderScope, scriptClassesDir, scriptMetadataDir, scriptCompileOperation, target.getScriptClass());
    }

    private void generateScriptPluginAdapter(PreCompiledGroovyScript scriptPlugin,
                                             CompiledScript<? extends BasicScript, ?> pluginsBlock,
                                             CompiledScript<? extends BasicScript, ?> buildScript) {
        String targetClass = scriptPlugin.getTargetClassName();
        File outputFile = generatedPluginAdaptersDir.file(scriptPlugin.getGeneratedPluginClassName() + ".java").get().getAsFile();

        String pluginsBlockClass = pluginsBlock.getRunDoesSomething() ? "Class.forName(\"" + scriptPlugin.getPluginsBlockClassName() + "\")" : null;
        String buildScriptClass = buildScript.getRunDoesSomething() ? "Class.forName(\"" + scriptPlugin.getClassName() + "\")" : null;

        try (BufferedWriter writer = Files.newBufferedWriter(Paths.get(outputFile.toURI()))) {
            writer.write("import " + targetClass + ";\n");
            writer.write("/**\n");
            writer.write(" * Precompiled " + scriptPlugin.getId() + " script plugin.\n");
            writer.write(" **/\n");
            writer.write("public class " + scriptPlugin.getGeneratedPluginClassName() + " implements org.gradle.api.Plugin<" + targetClass + "> {\n");
            writer.write("  public void apply(" + targetClass + " target) {\n");
            writer.write("      try {\n");
            writer.write("          Class<?> pluginsBlockClass = " + pluginsBlockClass + ";\n");
            writer.write("          Class<?> precompiledScriptClass = " + buildScriptClass + ";\n");
            writer.write("          new " + PreCompiledScriptRunner.class.getName() + "(target)\n");
            writer.write("              .run(\n");
            writer.write("                  pluginsBlockClass,\n");
            writer.write("                  precompiledScriptClass\n");
            writer.write("              );\n");
            writer.write("      } catch (Exception e) { throw new RuntimeException(e); }\n");
            writer.write("  }\n");
            writer.write("}\n");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static File subdirectory(DirectoryProperty root, String subdirPath) {
        return root.dir(subdirPath).get().getAsFile();
    }
}
