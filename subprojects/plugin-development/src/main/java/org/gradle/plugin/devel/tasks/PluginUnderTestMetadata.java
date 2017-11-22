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
import org.gradle.api.DefaultTask;
import org.gradle.api.Incubating;
import org.gradle.api.Transformer;
import org.gradle.api.UncheckedIOException;
import org.gradle.api.file.FileCollection;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;
import org.gradle.internal.classloader.ClasspathHasher;
import org.gradle.internal.classpath.DefaultClassPath;
import org.gradle.util.GUtil;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import java.util.Properties;

import static java.util.Collections.emptyList;
import static org.gradle.util.CollectionUtils.collect;

/**
 * Custom task for generating the metadata for a plugin user test.
 *
 * @since 2.13
 */
@Incubating
public class PluginUnderTestMetadata extends DefaultTask {

    public static final String IMPLEMENTATION_CLASSPATH_PROP_KEY = "implementation-classpath";
    public static final String IMPLEMENTATION_CLASSPATH_HASH_PROP_KEY = "implementation-classpath-hash";
    public static final String METADATA_FILE_NAME = "plugin-under-test-metadata.properties";
    private FileCollection pluginClasspath;
    private File outputDirectory;

    /**
     * The code under test. Defaults to {@code sourceSets.main.runtimeClasspath}.
     */
    @Classpath
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
        Properties properties = new Properties();

        if (getPluginClasspath() != null && !getPluginClasspath().isEmpty()) {
            properties.setProperty(IMPLEMENTATION_CLASSPATH_PROP_KEY, implementationClasspath());
            properties.setProperty(IMPLEMENTATION_CLASSPATH_HASH_PROP_KEY, implementationClasspathHash());
        }

        File outputFile = new File(getOutputDirectory(), METADATA_FILE_NAME);
        saveProperties(properties, outputFile);
    }

    private String implementationClasspath() {
        StringBuilder implementationClasspath = new StringBuilder();
        Joiner.on(File.pathSeparator).appendTo(implementationClasspath, getPaths());
        return implementationClasspath.toString();
    }

    private String implementationClasspathHash() {
        // As these files are inputs into this task, they have just been snapshotted by the task up-to-date checking.
        // We should be reusing those persistent snapshots to avoid reading into memory again.
        ClasspathHasher classpathHasher = getServices().get(ClasspathHasher.class);
        return classpathHasher.hash(new DefaultClassPath(getPluginClasspath())).toString();
    }

    private void saveProperties(Properties properties, File outputFile) {
        try {
            OutputStream outputStream = new BufferedOutputStream(new FileOutputStream(outputFile));
            GUtil.savePropertiesNoDateComment(properties, outputStream);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Input
    protected List<String> getPaths() {
        return collect(classpathFiles(), new Transformer<String, File>() {
            @Override
            public String transform(File file) {
                return file.getAbsolutePath().replaceAll("\\\\", "/");
            }
        });
    }

    private Iterable<File> classpathFiles() {
        if (getPluginClasspath() != null) {
            return getPluginClasspath();
        }
        return emptyList();
    }

}
