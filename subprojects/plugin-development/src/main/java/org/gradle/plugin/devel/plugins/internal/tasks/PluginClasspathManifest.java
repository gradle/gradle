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
import org.gradle.api.DefaultTask;
import org.gradle.api.Transformer;
import org.gradle.api.file.FileCollection;
import org.gradle.api.plugins.JavaPluginConvention;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.TaskAction;
import org.gradle.util.CollectionUtils;
import org.gradle.util.GFileUtils;

import java.io.File;
import java.util.List;

public class PluginClasspathManifest extends DefaultTask {

    private FileCollection pluginClasspath;
    private File outputFile;

    public PluginClasspathManifest() {
        pluginClasspath = getDefaultPluginClasspath();
        outputFile = getDefaultOutputFile();
    }

    private FileCollection getDefaultPluginClasspath() {
        JavaPluginConvention javaConvention = getProject().getConvention().getPlugin(JavaPluginConvention.class);
        SourceSet mainSourceSet = javaConvention.getSourceSets().findByName(SourceSet.MAIN_SOURCE_SET_NAME);
        return mainSourceSet != null ? mainSourceSet.getRuntimeClasspath() : null;
    }

    private File getDefaultOutputFile() {
        String pluginClasspath = String.format("%s/%s/plugin-classpath.txt", getProject().getBuildDir(), getName());
        return getProject().file(pluginClasspath);
    }

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
     * The target output file used for writing the classpath manifest. Defaults to {@code "$buildDir/$task.name/plugin-classpath.txt"}.
     */
    @OutputFile
    public File getOutputFile() {
        return outputFile;
    }

    @TaskAction
    public void generate() {
        List<String> paths = CollectionUtils.collect(getPluginClasspath(), new Transformer<String, File>() {
            @Override
            public String transform(File file) {
                return file.getAbsolutePath().replaceAll("\\\\", "/");
            }
        });

        String joinedPaths = Joiner.on("\n").join(paths);
        GFileUtils.writeFile(joinedPaths, getOutputFile());
    }
}
