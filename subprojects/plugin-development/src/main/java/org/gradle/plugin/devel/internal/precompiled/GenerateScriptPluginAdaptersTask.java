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
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;
import org.gradle.internal.UncheckedException;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

class GenerateScriptPluginAdaptersTask extends DefaultTask {
    private final Set<PreCompiledScript> scriptPlugins = new HashSet<>();
    private final DirectoryProperty generatedClassesDir;
    private final DirectoryProperty metadataDir;
    private final DirectoryProperty classesDir;

    public GenerateScriptPluginAdaptersTask() {
        this.generatedClassesDir = getProject().getObjects().directoryProperty();
        this.metadataDir = getProject().getObjects().directoryProperty();
        this.classesDir = getProject().getObjects().directoryProperty();
    }

    // TODO: figure out caching
    @PathSensitive(PathSensitivity.ABSOLUTE)
    @InputFiles
    Set<File> getScriptFiles() {
        return scriptPlugins.stream().map(PreCompiledScript::getScriptFile).collect(Collectors.toSet());
    }

    @OutputDirectory
    DirectoryProperty getGeneratedClassesDir() {
        return generatedClassesDir;
    }

    @Internal
    Set<PreCompiledScript> getScriptPlugins() {
        return scriptPlugins;
    }

    @Internal
    DirectoryProperty getMetadataDir() {
        return metadataDir;
    }

    @Internal
    DirectoryProperty getClassesDir() {
        return classesDir;
    }

    @TaskAction
    void generateScriptPluginAdapters() {
        File metadataDirValue = metadataDir.getAsFile().get();
        File classesDirValue = classesDir.getAsFile().get();

        for (PreCompiledScript scriptPlugin : scriptPlugins) {
            generateScriptPluginAdapter(scriptPlugin, classesDirValue, metadataDirValue);
        }
    }

    private void generateScriptPluginAdapter(PreCompiledScript scriptPlugin, File baseClassesDir, File baseMetadataDir) {
        File outputFile = generatedClassesDir.file(scriptPlugin.getGeneratedPluginClassName() + ".java").get().getAsFile();
        try (BufferedWriter writer = Files.newBufferedWriter(Paths.get(outputFile.toURI()))) {
            Optional<String> packageName = scriptPlugin.getGeneratedPluginPackage();
            if (packageName.isPresent()) {
                writer.write("package " + packageName.get() + ";\n\n");
            }
            writer.write("import org.gradle.api.Project;\n");
            writer.write("import org.gradle.api.internal.project.ProjectInternal;\n");
            writer.write("import org.gradle.plugin.devel.internal.precompiled.PreCompiledScriptRunner;\n");
            writer.write("import java.io.File;\n");
            writer.write("/**\n");
            writer.write(" * Precompiled " + scriptPlugin.getScriptFile().getName() + " script plugin.\n");
            writer.write(" **/\n");
            writer.write("public class " + scriptPlugin.getGeneratedPluginClassName() + " implements org.gradle.api.Plugin<org.gradle.api.Project> {\n");
            writer.write("  public void apply(Project project) {\n");
            writer.write("      new PreCompiledScriptRunner(\n");
            writer.write("          (ProjectInternal)project,");
            writer.write("         \"" + toEscapedPath(scriptPlugin.getScriptFile()) + "\",\n");
            writer.write("         \"" + toEscapedPath(baseClassesDir) + "\",\n");
            writer.write("         \"" + toEscapedPath(baseMetadataDir) + "\"\n");
            writer.write("      ).run();\n");
            writer.write("  }\n");
            writer.write("}\n");
        } catch (IOException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
    }

    private static String toEscapedPath(File file) {
        return file.toString().replace("\\", "\\\\");
    }
}
