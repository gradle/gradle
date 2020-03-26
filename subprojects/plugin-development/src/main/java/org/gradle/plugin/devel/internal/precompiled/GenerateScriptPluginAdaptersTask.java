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
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@CacheableTask
class GenerateScriptPluginAdaptersTask extends DefaultTask {
    private final List<PreCompiledScript> scriptPlugins = new ArrayList<>();
    private final DirectoryProperty generatedClassesDir = getProject().getObjects().directoryProperty();
    private final DirectoryProperty metadataDir = getProject().getObjects().directoryProperty();
    private final DirectoryProperty classesDir = getProject().getObjects().directoryProperty();

    public GenerateScriptPluginAdaptersTask() {
    }

    @PathSensitive(PathSensitivity.RELATIVE)
    @InputFiles
    Set<File> getScriptFiles() {
        return scriptPlugins.stream().map(PreCompiledScript::getScriptFile).collect(Collectors.toSet());
    }

    @OutputDirectory
    DirectoryProperty getGeneratedClassesDir() {
        return generatedClassesDir;
    }

    @Internal
    List<PreCompiledScript> getScriptPlugins() {
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
        String targetClass = scriptPlugin.getTargetClass().getName();
        File outputFile = generatedClassesDir.file(scriptPlugin.getGeneratedPluginClassName() + ".java").get().getAsFile();
        try (BufferedWriter writer = Files.newBufferedWriter(Paths.get(outputFile.toURI()))) {
            writer.write("import " + targetClass + ";\n");
            writer.write("/**\n");
            writer.write(" * Precompiled " + scriptPlugin.getScriptFile().getName() + " script plugin.\n");
            writer.write(" **/\n");
            writer.write("public class " + scriptPlugin.getGeneratedPluginClassName() + " implements org.gradle.api.Plugin<" + targetClass + "> {\n");
            writer.write("  public void apply(" + targetClass + " target) {\n");
            writer.write("      new " + scriptPlugin.getAdapterClass().getName() + "(\n");
            writer.write("              target,\n");
            writer.write("              \"" + toEscapedPath(scriptPlugin.getScriptFile()) + "\",\n");
            writer.write("              \"" + toEscapedPath(baseClassesDir) + "\",\n");
            writer.write("              \"" + toEscapedPath(baseMetadataDir) + "\"\n");
            writer.write("          ).run();\n");
            writer.write("  }\n");
            writer.write("}\n");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String toEscapedPath(File file) {
        return file.toString().replace("\\", "\\\\");
    }
}
