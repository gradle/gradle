/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.api.internal.tasks.cache;

import com.google.common.base.Function;
import com.google.common.base.Strings;
import com.google.common.base.Throwables;
import com.google.common.collect.Maps;
import com.google.common.io.Files;
import org.apache.commons.io.FileUtils;
import org.gradle.api.file.FileVisitDetails;
import org.gradle.api.file.FileVisitor;
import org.gradle.api.internal.TaskOutputsInternal;
import org.gradle.api.internal.file.collections.DirectoryFileTree;
import org.gradle.api.internal.tasks.CacheableTaskOutputFilePropertySpec;
import org.gradle.api.internal.tasks.CacheableTaskOutputFilePropertySpec.OutputType;
import org.gradle.api.internal.tasks.TaskFilePropertySpec;
import org.gradle.api.internal.tasks.TaskOutputFilePropertySpec;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public class ZipTaskOutputPacker implements TaskOutputPacker {
    @Override
    public void pack(TaskOutputsInternal taskOutputs, OutputStream output) throws IOException {
        final ZipOutputStream zipOutput = new ZipOutputStream(output);
        for (TaskOutputFilePropertySpec spec : taskOutputs.getFileProperties()) {
            CacheableTaskOutputFilePropertySpec propertySpec = (CacheableTaskOutputFilePropertySpec) spec;
            final String propertyName = propertySpec.getPropertyName();
            switch (propertySpec.getOutputType()) {
                case DIRECTORY:
                    final String propertyRoot = "property-" + propertyName + "/";
                    zipOutput.putNextEntry(new ZipEntry(propertyRoot));
                    new DirectoryFileTree(propertySpec.getOutputFile()).visit(new FileVisitor() {
                        @Override
                        public void visitDir(FileVisitDetails dirDetails) {
                            String path = dirDetails.getRelativePath().getPathString();
                            try {
                                zipOutput.putNextEntry(new ZipEntry(propertyRoot + path + "/"));
                            } catch (IOException e) {
                                throw Throwables.propagate(e);
                            }
                        }

                        @Override
                        public void visitFile(FileVisitDetails fileDetails) {
                            String path = fileDetails.getRelativePath().getPathString();
                            try {
                                zipOutput.putNextEntry(new ZipEntry(propertyRoot + path));
                                fileDetails.copyTo(zipOutput);
                            } catch (IOException e) {
                                throw Throwables.propagate(e);
                            }
                        }
                    });
                    break;
                case FILE:
                    try {
                        zipOutput.putNextEntry(new ZipEntry("property-" + propertyName));
                        Files.copy(propertySpec.getOutputFile(), zipOutput);
                    } catch (IOException e) {
                        throw Throwables.propagate(e);
                    }
                    break;
                default:
                    throw new AssertionError();
            }
        }
        zipOutput.finish();
    }

    private static final Pattern PROPERTY_PATH = Pattern.compile("property-([^/]+)(?:/(.*))?");

    @Override
    public void unpack(TaskOutputsInternal taskOutputs, InputStream input) throws IOException {
        Map<String, TaskOutputFilePropertySpec> propertySpecs = Maps.uniqueIndex(taskOutputs.getFileProperties(), new Function<TaskFilePropertySpec, String>() {
            @Override
            public String apply(TaskFilePropertySpec propertySpec) {
                return propertySpec.getPropertyName();
            }
        });
        ZipInputStream zipInput = new ZipInputStream(input);
        ZipEntry entry;
        while ((entry = zipInput.getNextEntry()) != null) {
            String name = entry.getName();
            Matcher matcher = PROPERTY_PATH.matcher(name);
            if (!matcher.matches()) {
                // TODO:LPTR What to do here?
                continue;
            }
            String propertyName = matcher.group(1);
            CacheableTaskOutputFilePropertySpec propertySpec = (CacheableTaskOutputFilePropertySpec) propertySpecs.get(propertyName);
            if (propertySpec == null) {
                throw new IllegalStateException(String.format("No output property '%s' registered", propertyName));
            }

            String path = matcher.group(2);
            File outputFile;
            if (Strings.isNullOrEmpty(path)) {
                outputFile = propertySpec.getOutputFile();
            } else {
                outputFile = new File(propertySpec.getOutputFile(), path);
            }
            if (entry.isDirectory()) {
                if (propertySpec.getOutputType() != OutputType.DIRECTORY) {
                    throw new IllegalStateException("Property should be an output directory property: " + propertyName);
                }
                FileUtils.forceMkdir(outputFile);
            } else {
                // TODO:LPTR Can we save on doing this?
                Files.createParentDirs(outputFile);
                Files.asByteSink(outputFile).writeFrom(zipInput);
            }
        }
    }
}
