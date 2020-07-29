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
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.FileSystemOperations;
import org.gradle.api.internal.initialization.ClassLoaderScope;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.SkipWhenEmpty;
import org.gradle.api.tasks.TaskAction;
import org.gradle.configuration.CompileOperationFactory;
import org.gradle.groovy.scripts.internal.CompileOperation;
import org.gradle.groovy.scripts.internal.CompiledScript;
import org.gradle.groovy.scripts.internal.ScriptCompilationHandler;
import org.gradle.initialization.ClassLoaderScopeRegistry;
import org.gradle.internal.classpath.DefaultClassPath;
import org.gradle.internal.exceptions.LocationAwareException;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.plugin.management.PluginRequest;
import org.gradle.plugin.management.internal.PluginRequests;
import org.gradle.plugin.use.internal.PluginsAwareScript;

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;

@CacheableTask
abstract class GeneratePluginAdaptersTask extends DefaultTask {
    private final ScriptCompilationHandler scriptCompilationHandler;
    private final CompileOperationFactory compileOperationFactory;
    private final ServiceRegistry serviceRegistry;
    private final FileSystemOperations fileSystemOperations;
    private final ClassLoaderScope classLoaderScope;

    @Inject
    public GeneratePluginAdaptersTask(ScriptCompilationHandler scriptCompilationHandler,
                                      ClassLoaderScopeRegistry classLoaderScopeRegistry,
                                      CompileOperationFactory compileOperationFactory,
                                      ServiceRegistry serviceRegistry, FileSystemOperations fileSystemOperations) {
        this.scriptCompilationHandler = scriptCompilationHandler;
        this.compileOperationFactory = compileOperationFactory;
        this.serviceRegistry = serviceRegistry;
        this.classLoaderScope = classLoaderScopeRegistry.getCoreAndPluginsScope();
        this.fileSystemOperations = fileSystemOperations;
    }

    @PathSensitive(PathSensitivity.RELATIVE)
    @InputFiles
    @SkipWhenEmpty
    abstract DirectoryProperty getExtractedPluginRequestsClassesDirectory();

    @OutputDirectory
    abstract DirectoryProperty getPluginAdapterSourcesOutputDirectory();

    @Internal
    abstract ListProperty<PrecompiledGroovyScript> getScriptPlugins();

    @TaskAction
    void generatePluginAdapters() {
        fileSystemOperations.delete(spec -> spec.delete(getPluginAdapterSourcesOutputDirectory()));
        getPluginAdapterSourcesOutputDirectory().get().getAsFile().mkdirs();

        // TODO: Use worker API?
        for (PrecompiledGroovyScript scriptPlugin : getScriptPlugins().get()) {
            PluginRequests pluginRequests = getValidPluginRequests(scriptPlugin);
            generateScriptPluginAdapter(scriptPlugin, pluginRequests);
        }
    }

    private PluginRequests getValidPluginRequests(PrecompiledGroovyScript scriptPlugin) {
        CompiledScript<PluginsAwareScript, ?> pluginsBlock = loadCompiledPluginsBlocks(scriptPlugin);
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
                        "requested plugin '%s' is an implementation dependency",
                    pluginRequest, pluginRequest.getId()));
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

    private CompiledScript<PluginsAwareScript, ?> loadCompiledPluginsBlocks(PrecompiledGroovyScript scriptPlugin) {
        CompileOperation<?> pluginsCompileOperation = compileOperationFactory.getPluginsBlockCompileOperation(scriptPlugin.getScriptTarget());
        File compiledPluginRequestsDir = getExtractedPluginRequestsClassesDirectory().get().dir(scriptPlugin.getId()).getAsFile();
        return scriptCompilationHandler.loadFromDir(scriptPlugin.getSource(), scriptPlugin.getContentHash(),
            classLoaderScope, DefaultClassPath.of(compiledPluginRequestsDir), compiledPluginRequestsDir, pluginsCompileOperation, PluginsAwareScript.class);
    }

    private void generateScriptPluginAdapter(PrecompiledGroovyScript scriptPlugin, PluginRequests pluginRequests) {
        String targetClass = scriptPlugin.getTargetClassName();
        File outputFile = getPluginAdapterSourcesOutputDirectory().file(scriptPlugin.getPluginAdapterClassName() + ".java").get().getAsFile();

        StringBuilder applyPlugins = new StringBuilder();
        if (!pluginRequests.isEmpty()) {
            for (PluginRequest pluginRequest : pluginRequests) {
                applyPlugins.append("        target.getPluginManager().apply(\"").append(pluginRequest.getId().getId()).append("\");\n");
            }
        }

        try (PrintWriter writer = new PrintWriter(Files.newBufferedWriter(Paths.get(outputFile.toURI())))) {
            writer.println("//CHECKSTYLE:OFF");
            writer.println("import org.gradle.util.GradleVersion;");
            writer.println("import org.gradle.groovy.scripts.BasicScript;");
            writer.println("import org.gradle.groovy.scripts.ScriptSource;");
            writer.println("import org.gradle.groovy.scripts.TextResourceScriptSource;");
            writer.println("import org.gradle.internal.resource.StringTextResource;");
            writer.println("/**");
            writer.println(" * Precompiled " + scriptPlugin.getId() + " script plugin.");
            writer.println(" **/");
            writer.println("public class " + scriptPlugin.getPluginAdapterClassName() + " implements org.gradle.api.Plugin<" + targetClass + "> {");
            writer.println("    private static final String MIN_SUPPORTED_GRADLE_VERSION = \"5.0\";");
            writer.println("    public void apply(" + targetClass + " target) {");
            writer.println("        assertSupportedByCurrentGradleVersion();");
            writer.println("        " + applyPlugins + "");
            writer.println("        try {");
            writer.println("            Class<? extends BasicScript> precompiledScriptClass = Class.forName(\"" + scriptPlugin.getClassName() + "\").asSubclass(BasicScript.class);");
            writer.println("            BasicScript script = precompiledScriptClass.getDeclaredConstructor().newInstance();");
            writer.println("            script.setScriptSource(scriptSource(precompiledScriptClass));");
            writer.println("            script.init(target, " + scriptPlugin.serviceRegistryAccessCode() + ");");
            writer.println("            script.run();");
            writer.println("        } catch (Exception e) {");
            writer.println("            throw new RuntimeException(e);");
            writer.println("        }");
            writer.println("  }");
            writer.println("  private static ScriptSource scriptSource(Class<?> scriptClass) {");
            writer.println("      return new TextResourceScriptSource(new StringTextResource(scriptClass.getSimpleName(), \"\"));");
            writer.println("  }");
            writer.println("  private static void assertSupportedByCurrentGradleVersion() {");
            writer.println("      if (GradleVersion.current().getBaseVersion().compareTo(GradleVersion.version(MIN_SUPPORTED_GRADLE_VERSION)) < 0) {");
            writer.println("          throw new RuntimeException(\"Precompiled Groovy script plugins require Gradle \"+MIN_SUPPORTED_GRADLE_VERSION+\" or higher\");");
            writer.println("      }");
            writer.println("  }");
            writer.println("}");
            writer.println("//CHECKSTYLE:ON");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
