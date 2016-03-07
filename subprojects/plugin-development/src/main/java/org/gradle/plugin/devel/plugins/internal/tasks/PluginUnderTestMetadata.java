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

package org.gradle.plugin.devel.plugins.internal.tasks;

import com.google.common.base.Joiner;
import org.apache.commons.io.IOUtils;
import org.gradle.api.DefaultTask;
import org.gradle.api.Transformer;
import org.gradle.api.UncheckedIOException;
import org.gradle.api.file.FileCollection;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;
import org.gradle.util.CollectionUtils;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;

/**
 * Custom task for generating the metadata for a plugin user test.
 *
 * @since 2.13
 */
public class PluginUnderTestMetadata extends DefaultTask {

    public static final String IMPLEMENTATION_CLASSPATH_PROP_KEY = "implementation-classpath";
    public static final String METADATA_FILE_NAME = "plugin-under-test-metadata.properties";
    private FileCollection pluginClasspath;
    private File outputDirectory;

    /**
     * The code under test. Defaults to {@code sourceSets.main.runtimeClasspath}.
     */
    @InputFiles
    public FileCollection getPluginClasspath() {
        return pluginClasspath;
    }

    public void setPluginClasspath(FileCollection pluginClasspath) {
        this.pluginClasspath = pluginClasspath;
    }

    /**
     * The target output directory used for writing the classpath manifest. Defaults to {@code "$buildDir/$task.name"}.
     */
    @OutputDirectory
    public File getOutputDirectory() {
        return outputDirectory;
    }

    public void setOutputDirectory(File outputDirectory) {
        this.outputDirectory = outputDirectory;
    }

    @TaskAction
    public void generate() {
        FileWriter writer = null;

        try {
            writer = new FileWriter(new File(getOutputDirectory(), METADATA_FILE_NAME));

            if (!getPluginClasspath().isEmpty()) {
                List<String> paths = CollectionUtils.collect(getPluginClasspath(), new Transformer<String, File>() {
                    @Override
                    public String transform(File file) {
                        return file.getAbsolutePath().replaceAll("\\\\", "/");
                    }
                });

                writer.write(IMPLEMENTATION_CLASSPATH_PROP_KEY);
                writer.write("=");
                Joiner.on(",").appendTo(writer, paths);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } finally {
            IOUtils.closeQuietly(writer);
        }
    }
}
