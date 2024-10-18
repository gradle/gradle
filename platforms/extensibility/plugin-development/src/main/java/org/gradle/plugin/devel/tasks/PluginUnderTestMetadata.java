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

package org.gradle.plugin.devel.tasks;

import com.google.common.base.Joiner;
import org.apache.commons.io.FilenameUtils;
import org.gradle.api.DefaultTask;
import org.gradle.api.UncheckedIOException;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;
import org.gradle.internal.util.PropertiesUtils;
import org.gradle.work.DisableCachingByDefault;

import java.io.File;
import java.io.IOException;
import java.util.Properties;

import static org.gradle.util.internal.CollectionUtils.collect;

/**
 * Custom task for generating the metadata for a plugin user test.
 *
 * @since 2.13
 */
@DisableCachingByDefault(because = "Not worth caching")
public abstract class PluginUnderTestMetadata extends DefaultTask {

    public static final String IMPLEMENTATION_CLASSPATH_PROP_KEY = "implementation-classpath";
    public static final String METADATA_FILE_NAME = "plugin-under-test-metadata.properties";

    /**
     * The code under test. Defaults to {@code sourceSets.main.runtimeClasspath}.
     *
     * Note: this is not a @Classpath since the ablosute paths are written to the metadata file.
     */
    @InputFiles
    @PathSensitive(PathSensitivity.ABSOLUTE)
    public abstract ConfigurableFileCollection getPluginClasspath();

    /**
     * The target output directory used for writing the classpath manifest. Defaults to {@code "$buildDir/$task.name"}.
     */
    @OutputDirectory
    public abstract DirectoryProperty getOutputDirectory();

    @TaskAction
    public void generate() {
        Properties properties = new Properties();

        if (!getPluginClasspath().isEmpty()) {
            properties.setProperty(IMPLEMENTATION_CLASSPATH_PROP_KEY, implementationClasspath());
        }

        File outputFile = new File(getOutputDirectory().get().getAsFile(), METADATA_FILE_NAME);
        saveProperties(properties, outputFile);
    }

    private String implementationClasspath() {
        StringBuilder implementationClasspath = new StringBuilder();
        Joiner.on(File.pathSeparator).appendTo(
            implementationClasspath,
            collect(getPluginClasspath(), file -> FilenameUtils.separatorsToUnix(file.getAbsolutePath()))
        );
        return implementationClasspath.toString();
    }

    private void saveProperties(Properties properties, File outputFile) {
        try {
            PropertiesUtils.store(properties, outputFile);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

}
