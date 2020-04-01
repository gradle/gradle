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
import org.gradle.groovy.scripts.BasicScript;
import org.gradle.groovy.scripts.ScriptRunner;
import org.gradle.groovy.scripts.internal.BuildScriptData;
import org.gradle.groovy.scripts.internal.CompileOperation;
import org.gradle.groovy.scripts.internal.CompiledScript;
import org.gradle.groovy.scripts.internal.ScriptCompilationHandler;
import org.gradle.groovy.scripts.internal.ScriptRunnerFactory;
import org.gradle.initialization.ClassLoaderScopeRegistry;
import org.gradle.internal.Actions;
import org.gradle.internal.classpath.DefaultClassPath;
import org.gradle.internal.exceptions.LocationAwareException;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.model.dsl.internal.transform.ClosureCreationInterceptingVerifier;
import org.gradle.plugin.management.PluginRequest;
import org.gradle.plugin.management.internal.PluginRequests;
import org.gradle.plugin.use.internal.PluginsAwareScript;
import org.gradle.testfixtures.ProjectBuilder;

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

@CacheableTask
class PrecompileGroovyScriptsTask extends DefaultTask {

    private final Project project = getProject();

    private final ScriptCompilationHandler scriptCompilationHandler;
    private final CompileOperationFactory compileOperationFactory;
    private final FileSystemOperations fileSystemOperations;
    private final ScriptRunnerFactory scriptRunnerFactory;
    private final ServiceRegistry serviceRegistry;

    private final ClassLoaderScope classLoaderScope;

    private final Set<File> pluginSourceFiles;
    private final List<PrecompiledGroovyScript> scriptPlugins;

    private final Provider<Directory> intermediatePluginClassesDir;
    private final Provider<Directory> intermediatePluginMetadataDir;

    private final DirectoryProperty precompiledGroovyScriptsOutputDir = project.getObjects().directoryProperty();
    private final DirectoryProperty pluginAdapterSourcesOutputDir = project.getObjects().directoryProperty();
    private final DirectoryProperty pluginAdapterClassesOutputDir = project.getObjects().directoryProperty();

    private final Provider<Directory> javaSourceDependencyClasses;
    private final Provider<Directory> groovySourceDependencyClasses;

    private final FileCollection classpath;

    @Inject
    public PrecompileGroovyScriptsTask(ScriptCompilationHandler scriptCompilationHandler,
                                       ClassLoaderScopeRegistry classLoaderScopeRegistry,
                                       CompileOperationFactory compileOperationFactory,
                                       FileSystemOperations fileSystemOperations,
                                       ScriptRunnerFactory scriptRunnerFactory, ServiceRegistry serviceRegistry, Set<File> pluginSourceFiles,
                                       List<PrecompiledGroovyScript> scriptPlugins,
                                       FileCollection classpath,
                                       Provider<Directory> javaSourceDependencyClasses,
                                       Provider<Directory> groovySourceDependencyClasses) {
        this.scriptCompilationHandler = scriptCompilationHandler;
        this.compileOperationFactory = compileOperationFactory;
        this.fileSystemOperations = fileSystemOperations;
        this.scriptRunnerFactory = scriptRunnerFactory;
        this.serviceRegistry = serviceRegistry;

        this.classLoaderScope = classLoaderScopeRegistry.getCoreAndPluginsScope();

        this.pluginSourceFiles = pluginSourceFiles;
        this.scriptPlugins = scriptPlugins;
        this.classpath = classpath;

        DirectoryProperty buildDir = project.getLayout().getBuildDirectory();
        this.intermediatePluginClassesDir = buildDir.dir("groovy-dsl-plugins/work/classes");
        this.intermediatePluginMetadataDir = buildDir.dir("groovy-dsl-plugins/work/metadata");

        this.precompiledGroovyScriptsOutputDir.set(buildDir.dir("groovy-dsl-plugins/output/plugin-classes"));
        this.pluginAdapterSourcesOutputDir.set(buildDir.dir("groovy-dsl-plugins/output/adapter-src"));
        this.pluginAdapterClassesOutputDir.set(buildDir.dir("groovy-dsl-plugins/output/adapter-classes"));

        this.javaSourceDependencyClasses = javaSourceDependencyClasses;
        this.groovySourceDependencyClasses = groovySourceDependencyClasses;
    }

    @PathSensitive(PathSensitivity.RELATIVE)
    @InputFiles
    Set<File> getScriptFiles() {
        return pluginSourceFiles;
    }

    @PathSensitive(PathSensitivity.RELATIVE)
    @InputFiles
    Provider<Directory> getJavaSourceDependencyClasses() {
        return javaSourceDependencyClasses;
    }

    @PathSensitive(PathSensitivity.RELATIVE)
    @InputFiles
    Provider<Directory> getGroovySourceDependencyClasses() {
        return groovySourceDependencyClasses;
    }

    @Classpath
    public FileCollection getClasspath() {
        return classpath;
    }

    @OutputDirectory
    Provider<File> getPrecompiledGroovyScriptsOutputDir() {
        return precompiledGroovyScriptsOutputDir.getAsFile();
    }

    @OutputDirectory
    DirectoryProperty getPluginAdapterSourcesOutputDir() {
        return pluginAdapterSourcesOutputDir;
    }

    @Internal
    Provider<File> getAdapterClassesOutputDir() {
        return pluginAdapterClassesOutputDir.getAsFile();
    }

    @TaskAction
    void compileScripts() {
        FileCollection compileClasspath = classpath.plus(project.files(javaSourceDependencyClasses, groovySourceDependencyClasses));
        ClassLoader compileClassLoader = new URLClassLoader(DefaultClassPath.of(compileClasspath).getAsURLArray(), classLoaderScope.getLocalClassLoader());

        for (PrecompiledGroovyScript scriptPlugin : scriptPlugins) {
            CompiledScript<PluginsAwareScript, ?> pluginsBlock = compilePluginsBlock(scriptPlugin, compileClassLoader);
            validatePluginRequests(pluginsBlock, scriptPlugin, compileClassLoader);
            CompiledScript<? extends BasicScript, ?> buildScript = compileBuildScript(scriptPlugin, compileClassLoader);
            generateScriptPluginAdapter(scriptPlugin, pluginsBlock, buildScript);
        }

        fileSystemOperations.copy(copySpec -> {
            copySpec.from(intermediatePluginMetadataDir.get(), intermediatePluginClassesDir.get().getAsFileTree().getFiles());
            copySpec.into(precompiledGroovyScriptsOutputDir);
        });
    }

    private void validatePluginRequests(CompiledScript<PluginsAwareScript, ?> pluginsBlock, PrecompiledGroovyScript scriptPlugin, ClassLoader compileClassLoader) {
        if (pluginsBlock.getRunDoesSomething()) {
            PluginRequests pluginRequests = extractPluginRequests(pluginsBlock, scriptPlugin, compileClassLoader);
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
        }
    }

    private PluginRequests extractPluginRequests(CompiledScript<PluginsAwareScript, ?> pluginsBlock, PrecompiledGroovyScript scriptPlugin, ClassLoader compileClassLoader) {
        ScriptRunner<PluginsAwareScript, ?> runner = scriptRunnerFactory.create(pluginsBlock, scriptPlugin.getPluginsBlockSource(), compileClassLoader);

        Project target = ProjectBuilder.builder().withParent(project).build();
        runner.run(target, serviceRegistry);

        return runner.getScript().getPluginRequests();
    }

    private CompiledScript<PluginsAwareScript, ?> compilePluginsBlock(PrecompiledGroovyScript scriptPlugin, ClassLoader compileClassLoader) {
        CompileOperation<?> pluginsCompileOperation = compileOperationFactory.getPluginsBlockCompileOperation(scriptPlugin.getScriptTarget());
        String targetPath = scriptPlugin.getPluginsBlockClassName();
        File pluginsMetadataDir = subdirectory(intermediatePluginMetadataDir, targetPath);
        File pluginsClassesDir = subdirectory(intermediatePluginClassesDir, targetPath);
        scriptCompilationHandler.compileToDir(
            scriptPlugin.getPluginsBlockSource(), compileClassLoader, pluginsClassesDir, pluginsMetadataDir, pluginsCompileOperation,
            PluginsAwareScript.class, Actions.doNothing());

        return scriptCompilationHandler.loadFromDir(scriptPlugin.getPluginsBlockSource(), scriptPlugin.getContentHash(),
            classLoaderScope, pluginsClassesDir, pluginsMetadataDir, pluginsCompileOperation, PluginsAwareScript.class);
    }

    private CompiledScript<? extends BasicScript, ?> compileBuildScript(PrecompiledGroovyScript scriptPlugin, ClassLoader compileClassLoader) {
        ScriptTarget target = scriptPlugin.getScriptTarget();
        CompileOperation<BuildScriptData> scriptCompileOperation = compileOperationFactory.getScriptCompileOperation(scriptPlugin.getSource(), target);
        String targetPath = scriptPlugin.getClassName();
        File scriptMetadataDir = subdirectory(intermediatePluginMetadataDir, targetPath);
        File scriptClassesDir = subdirectory(intermediatePluginClassesDir, targetPath);
        scriptCompilationHandler.compileToDir(
            scriptPlugin.getSource(), compileClassLoader, scriptClassesDir,
            scriptMetadataDir, scriptCompileOperation, target.getScriptClass(),
            ClosureCreationInterceptingVerifier.INSTANCE);

        return scriptCompilationHandler.loadFromDir(scriptPlugin.getSource(), scriptPlugin.getContentHash(),
            classLoaderScope, scriptClassesDir, scriptMetadataDir, scriptCompileOperation, target.getScriptClass());
    }

    private void generateScriptPluginAdapter(PrecompiledGroovyScript scriptPlugin,
                                             CompiledScript<? extends BasicScript, ?> pluginsBlock,
                                             CompiledScript<? extends BasicScript, ?> buildScript) {
        String targetClass = scriptPlugin.getTargetClassName();
        File outputFile = pluginAdapterSourcesOutputDir.file(scriptPlugin.getGeneratedPluginClassName() + ".java").get().getAsFile();

        String pluginsBlockClass = pluginsBlock.getRunDoesSomething() ? "Class.forName(\"" + scriptPlugin.getPluginsBlockClassName() + "\")" : null;
        String buildScriptClass = buildScript.getRunDoesSomething() ? "Class.forName(\"" + scriptPlugin.getClassName() + "\")" : null;

        try (BufferedWriter writer = Files.newBufferedWriter(Paths.get(outputFile.toURI()))) {
            writer.write("import " + targetClass + ";\n");
            writer.write("import org.gradle.util.GradleVersion;\n");
            writer.write("/**\n");
            writer.write(" * Precompiled " + scriptPlugin.getId() + " script plugin.\n");
            writer.write(" **/\n");
            writer.write("public class " + scriptPlugin.getGeneratedPluginClassName() + " implements org.gradle.api.Plugin<" + targetClass + "> {\n");
            writer.write("  private static final String MIN_SUPPORTED_GRADLE_VERSION = \"6.4\";\n");
            writer.write("  public void apply(" + targetClass + " target) {\n");
            writer.write("      assertSupportedByCurrentGradleVersion();\n");
            writer.write("      try {\n");
            writer.write("          Class<?> pluginsBlockClass = " + pluginsBlockClass + ";\n");
            writer.write("          Class<?> precompiledScriptClass = " + buildScriptClass + ";\n");
            writer.write("          new " + PrecompiledScriptRunner.class.getName() + "(target)\n");
            writer.write("              .run(\n");
            writer.write("                  pluginsBlockClass,\n");
            writer.write("                  precompiledScriptClass\n");
            writer.write("              );\n");
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
