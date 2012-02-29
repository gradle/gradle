/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.api.internal.tasks.compile;

import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import org.gradle.api.JavaVersion;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.file.collections.SimpleFileCollection;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.tasks.compile.CompileOptions;
import org.gradle.api.tasks.compile.ForkOptions;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class JavaCommandLineOptionsBuilder {
    private static final Logger LOGGER = Logging.getLogger(JavaCommandLineOptionsBuilder.class);

    private final JavaCompileSpec spec;

    private boolean includeLauncherOptions;
    private boolean includeSourceFiles;
    private boolean logGeneratedOptions = true;

    private final List<String> options = new ArrayList<String>();

    public JavaCommandLineOptionsBuilder(JavaCompileSpec spec) {
        this.spec = spec;
    }

    public JavaCommandLineOptionsBuilder includeLauncherOptions(boolean flag) {
        includeLauncherOptions = flag;
        return this;
    }

    public JavaCommandLineOptionsBuilder includeSourceFiles(boolean flag) {
        includeSourceFiles = flag;
        return this;
    }

    public JavaCommandLineOptionsBuilder logGeneratedOptions(boolean flag) {
        logGeneratedOptions = flag;
        return this;
    }

    public List<String> build() {
        options.clear();

        addMainOptions();
        addLauncherOptions();
        addSourceFiles();
        logOptions();

        return options;
    }

    private void addMainOptions() {
        String sourceCompatibility = spec.getSourceCompatibility();
        if (sourceCompatibility != null && !JavaVersion.current().equals(JavaVersion.toVersion(sourceCompatibility))) {
            options.add("-source");
            options.add(sourceCompatibility);
        }
        String targetCompatibility = spec.getTargetCompatibility();
        if (targetCompatibility != null && !JavaVersion.current().equals(JavaVersion.toVersion(targetCompatibility))) {
            options.add("-target");
            options.add(targetCompatibility);
        }
        File destinationDir = spec.getDestinationDir();
        if (destinationDir != null) {
            options.add("-d");
            options.add(destinationDir.getPath());
        }
        CompileOptions compileOptions = spec.getCompileOptions();
        if (compileOptions.isVerbose()) {
            options.add("-verbose");
        }
        if (compileOptions.isDeprecation()) {
            options.add("-deprecation");
        }
        if (!compileOptions.isWarnings()) {
            options.add("-nowarn");
        }
        if (!compileOptions.isDebug()) {
            options.add("-g:none");
        }
        if (compileOptions.getEncoding() != null) {
            options.add("-encoding");
            options.add(compileOptions.getEncoding());
        }
        if (compileOptions.getBootClasspath() != null) {
            options.add("-bootclasspath");
            options.add(compileOptions.getBootClasspath());
        }
        if (compileOptions.getExtensionDirs() != null) {
            options.add("-extdirs");
            options.add(compileOptions.getExtensionDirs());
        }
        Iterable<File> classpath = spec.getClasspath();
        if (classpath != null && classpath.iterator().hasNext()) {
            options.add("-classpath");
            options.add(toFileCollection(classpath).getAsPath());
        }
        if (compileOptions.getCompilerArgs() != null) {
            options.addAll(compileOptions.getCompilerArgs());
        }
    }

    private void addLauncherOptions() {
        if (!includeLauncherOptions) { return; }

        ForkOptions forkOptions = spec.getCompileOptions().getForkOptions();
        if (forkOptions.getMemoryInitialSize() != null) {
            options.add("-J-Xms" + forkOptions.getMemoryInitialSize());
        }
        if (forkOptions.getMemoryMaximumSize() != null) {
            options.add("-J-Xmx" + forkOptions.getMemoryMaximumSize());
        }
    }

    private void addSourceFiles() {
        if (!includeSourceFiles) { return; }

        for (File file : spec.getSource()) {
            options.add(file.getPath());
        }
    }

    private void logOptions() {
        if (logGeneratedOptions && LOGGER.isInfoEnabled()) {
            LOGGER.info("Invoking Java compiler with options '{}'", Joiner.on(' ').join(options));
        }
    }

    private FileCollection toFileCollection(Iterable<File> classpath) {
        if (classpath instanceof FileCollection) {
            return (FileCollection) classpath;
        }
        return new SimpleFileCollection(Lists.newArrayList(classpath));
    }
}
