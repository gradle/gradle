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
import org.gradle.api.UncheckedIOException;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.Directory;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.FileSystemOperations;
import org.gradle.api.internal.initialization.ClassLoaderScope;
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
import org.gradle.groovy.scripts.internal.CompiledScript;
import org.gradle.groovy.scripts.internal.ScriptCompilationHandler;
import org.gradle.initialization.ClassLoaderScopeRegistry;
import org.gradle.internal.Actions;
import org.gradle.internal.classpath.DefaultClassPath;
import org.gradle.internal.exceptions.LocationAwareException;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.model.dsl.internal.transform.ClosureCreationInterceptingVerifier;
import org.gradle.plugin.management.PluginRequest;
import org.gradle.plugin.management.internal.PluginRequests;
import org.gradle.plugin.use.internal.PluginsAwareScript;

import javax.inject.Inject;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@CacheableTask
abstract class PrecompileGroovyScriptsTask extends DefaultTask {

    private final Project project = getProject();

    private final ScriptCompilationHandler scriptCompilationHandler;
    private final CompileOperationFactory compileOperationFactory;
    private final FileSystemOperations fileSystemOperations;
    private final ServiceRegistry serviceRegistry;

    private final ClassLoaderScope classLoaderScope;

    private final List<PrecompiledGroovyScript> scriptPlugins;

    private final Provider<Directory> intermediatePluginBlockClassesDir;
    private final Provider<Directory> intermediatePluginClassesDir;
    private final Provider<Directory> intermediatePluginMetadataDir;

    @Inject
    public PrecompileGroovyScriptsTask(ScriptCompilationHandler scriptCompilationHandler,
                                       ClassLoaderScopeRegistry classLoaderScopeRegistry,
                                       CompileOperationFactory compileOperationFactory,
                                       FileSystemOperations fileSystemOperations,
                                       ServiceRegistry serviceRegistry,
                                       List<PrecompiledGroovyScript> scriptPlugins) {
        this.scriptCompilationHandler = scriptCompilationHandler;
        this.compileOperationFactory = compileOperationFactory;
        this.fileSystemOperations = fileSystemOperations;
        this.serviceRegistry = serviceRegistry;

        this.classLoaderScope = classLoaderScopeRegistry.getCoreAndPluginsScope();

        this.scriptPlugins = scriptPlugins;

        DirectoryProperty buildDir = project.getLayout().getBuildDirectory();
        this.intermediatePluginBlockClassesDir = buildDir.dir("groovy-dsl-plugins/plugin-blocks");
        this.intermediatePluginClassesDir = buildDir.dir("groovy-dsl-plugins/work/classes");
        this.intermediatePluginMetadataDir = buildDir.dir("groovy-dsl-plugins/work/metadata");
    }

    @PathSensitive(PathSensitivity.RELATIVE)
    @InputFiles
    Set<File> getScriptFiles() {
        return scriptPlugins.stream().map(p -> p.getSource().getResource().getFile()).collect(Collectors.toSet());
    }

    @Classpath
    abstract ConfigurableFileCollection getClasspath();

    @OutputDirectory
    abstract DirectoryProperty getPrecompiledGroovyScriptsOutputDir();

    @OutputDirectory
    abstract DirectoryProperty getPluginAdapterSourcesOutputDir();

    @Internal
    abstract DirectoryProperty getAdapterClassesOutputDir();

    @TaskAction
    void compileScripts() {
        FileCollection compileClasspath = getClasspath();
        ClassLoader compileClassLoader = new URLClassLoader(DefaultClassPath.of(compileClasspath).getAsURLArray());

        for (PrecompiledGroovyScript scriptPlugin : scriptPlugins) {
            CompiledScript<PluginsAwareScript, ?> pluginsBlock = compilePluginsBlock(scriptPlugin, compileClassLoader);
            PluginRequests pluginRequests = getValidPluginRequests(pluginsBlock, scriptPlugin);
            compileBuildScript(scriptPlugin, compileClassLoader);
            generateScriptPluginAdapter(scriptPlugin, pluginRequests);
        }

        fileSystemOperations.copy(copySpec -> {
            copySpec.from(intermediatePluginClassesDir.get().getAsFileTree().getFiles());
            copySpec.into(getPrecompiledGroovyScriptsOutputDir());
        });
    }

    private PluginRequests getValidPluginRequests(CompiledScript<PluginsAwareScript, ?> pluginsBlock, PrecompiledGroovyScript scriptPlugin) {
        if (!pluginsBlock.getRunDoesSomething()) {
            return PluginRequests.EMPTY;
        }
        PluginRequests pluginRequests = extractPluginRequests(pluginsBlock, scriptPlugin);
        Set<String> validationErrors = new HashSet<>();
        for (PluginRequest pluginRequest : pluginRequests) {
            if (pluginRequest.getVersion() != null) {
                validationErrors.add(String.format("Invalid plugin request %s. " +
                        "Plugin requests from precompiled scripts must not include a version number. " +
                        "Please remove the version from the offending request and make sure the module containing the " +
                        "requested plugin '%s' is an implementation dependency of %s",
                    pluginRequest, pluginRequest.getId(), project));
            }
        }
        if (!validationErrors.isEmpty()) {
            throw new LocationAwareException(new IllegalArgumentException(String.join("\n", validationErrors)),
                scriptPlugin.getSource().getResource().getLocation().getDisplayName(),
                pluginRequests.iterator().next().getLineNumber());
        }
        return pluginRequests;
    }

    private PluginRequests extractPluginRequests(CompiledScript<PluginsAwareScript, ?> pluginsBlock, PrecompiledGroovyScript scriptPlugin) {
        try {
            PluginsAwareScript pluginsAwareScript = pluginsBlock.loadClass().getDeclaredConstructor().newInstance();
            pluginsAwareScript.setScriptSource(scriptPlugin.getSource());
            pluginsAwareScript.init("dummy", serviceRegistry);
            pluginsAwareScript.run();
            return pluginsAwareScript.getPluginRequests();
        } catch (Exception e) {
            throw new IllegalStateException("Could not execute plugins block", e);
        }
    }

    private CompiledScript<PluginsAwareScript, ?> compilePluginsBlock(PrecompiledGroovyScript scriptPlugin, ClassLoader compileClassLoader) {
        CompileOperation<?> pluginsCompileOperation = compileOperationFactory.getPluginsBlockCompileOperation(scriptPlugin.getScriptTarget());
        File outputDir = intermediatePluginBlockClassesDir.get().getAsFile();
        scriptCompilationHandler.compileToDir(
            scriptPlugin.getSource(), compileClassLoader, outputDir, outputDir, pluginsCompileOperation,
            PluginsAwareScript.class, Actions.doNothing());

        return scriptCompilationHandler.loadFromDir(scriptPlugin.getSource(), scriptPlugin.getContentHash(),
            classLoaderScope, outputDir, outputDir, pluginsCompileOperation, PluginsAwareScript.class);
    }

    private void compileBuildScript(PrecompiledGroovyScript scriptPlugin, ClassLoader compileClassLoader) {
        ScriptTarget target = scriptPlugin.getScriptTarget();
        CompileOperation<BuildScriptData> scriptCompileOperation = compileOperationFactory.getScriptCompileOperation(scriptPlugin.getSource(), target);
        String targetPath = scriptPlugin.getClassName();
        File scriptMetadataDir = subdirectory(intermediatePluginMetadataDir, targetPath);
        File scriptClassesDir = subdirectory(intermediatePluginClassesDir, targetPath);
        scriptCompilationHandler.compileToDir(
            scriptPlugin.getSource(), compileClassLoader, scriptClassesDir,
            scriptMetadataDir, scriptCompileOperation, target.getScriptClass(),
            ClosureCreationInterceptingVerifier.INSTANCE);
    }

    private void generateScriptPluginAdapter(PrecompiledGroovyScript scriptPlugin, PluginRequests pluginRequests) {
        String targetClass = scriptPlugin.getTargetClassName();
        File outputFile = getPluginAdapterSourcesOutputDir().file(scriptPlugin.getGeneratedPluginClassName() + ".java").get().getAsFile();

        StringBuilder pluginImports = new StringBuilder();
        StringBuilder applyPlugins = new StringBuilder();
        if (!pluginRequests.isEmpty()) {
            pluginImports.append("import java.util.Map;\n").append("import java.util.HashMap;\n");
            applyPlugins.append("Map<String, String> plugins = new HashMap<>(); ");
            for (PluginRequest pluginRequest : pluginRequests) {
                applyPlugins.append("plugins.put(\"plugin\", \"").append(pluginRequest.getId().getId()).append("\"); ");
            }
            applyPlugins.append("target.apply(plugins);");
        }

        String buildScriptClass = "Class.forName(\"" + scriptPlugin.getClassName() + "\")";

        try (BufferedWriter writer = Files.newBufferedWriter(Paths.get(outputFile.toURI()))) {
            writer.write("import " + targetClass + ";\n");
            writer.write("import org.gradle.util.GradleVersion;\n");
            writer.write(pluginImports + "\n");
            writer.write("/**\n");
            writer.write(" * Precompiled " + scriptPlugin.getId() + " script plugin.\n");
            writer.write(" **/\n");
            writer.write("public class " + scriptPlugin.getGeneratedPluginClassName() + " implements org.gradle.api.Plugin<" + targetClass + "> {\n");
            writer.write("  private static final String MIN_SUPPORTED_GRADLE_VERSION = \"6.4\";\n");
            writer.write("  public void apply(" + targetClass + " target) {\n");
            writer.write("      assertSupportedByCurrentGradleVersion();\n");
            writer.write("      " + applyPlugins + "\n");
            writer.write("      try {\n");
            writer.write("          Class<?> precompiledScriptClass = " + buildScriptClass + ";\n");
            writer.write("          new " + PrecompiledScriptRunner.class.getName() + "(target)\n");
            writer.write("              .run(precompiledScriptClass);\n");
            writer.write("      } catch (Exception e) {\n");
            writer.write("          throw new RuntimeException(e);\n");
            writer.write("      }\n");
            writer.write("  }\n");
            writer.write("  private static void assertSupportedByCurrentGradleVersion() {\n");
            writer.write("      if (GradleVersion.current().getBaseVersion().compareTo(GradleVersion.version(MIN_SUPPORTED_GRADLE_VERSION)) < 0) {\n");
            writer.write("          throw new RuntimeException(\"Precompiled Groovy script plugins require Gradle \"+MIN_SUPPORTED_GRADLE_VERSION+\" or higher\");\n");
            writer.write("      }\n");
            writer.write("  }\n");
            writer.write("}\n");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static File subdirectory(Provider<Directory> root, String subdirPath) {
        return root.get().dir(subdirPath).getAsFile();
    }

}
