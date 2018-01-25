/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.swiftpm.tasks;

import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;

public class GenerateSwiftPackageManagerManifest extends DefaultTask {
    private final RegularFileProperty manifestFile = newOutputFile();

    @OutputFile
    public RegularFileProperty getManifestFile() {
        return manifestFile;
    }

    @TaskAction
    public void generate() {
        File file = manifestFile.get().getAsFile();
        file.getParentFile().mkdirs();
        try {
            PrintWriter writer = new PrintWriter(new FileWriter(file));
            try {
                writer.println("// swift-tools-version:4.0");
                writer.println("//");
                writer.println("// GENERATED FILE - do not edit");
                writer.println("//");
                writer.println("import PackageDescription");
                writer.println();
                writer.println("let package = Package(");
                writer.println("  name: \"" + getProject().getName() + "\"");
                writer.println(")");
            } finally {
                writer.close();
            }
        } catch (IOException e) {
            throw new GradleException(String.format("Could not write manifest file %s.", file), e);
        }
    }
}
