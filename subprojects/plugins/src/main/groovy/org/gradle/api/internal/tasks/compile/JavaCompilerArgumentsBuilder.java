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

import com.google.common.collect.Lists;
import org.gradle.api.JavaVersion;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.file.collections.SimpleFileCollection;
import org.gradle.api.tasks.compile.CompileOptions;
import org.gradle.api.tasks.compile.ForkOptions;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class JavaCompilerArgumentsBuilder {
    private final JavaCompileSpec spec;

    private boolean includeLauncherOptions;
    private boolean includeMainOptions = true;
    private boolean includeClasspath = true;
    private boolean includeSourceFiles;

    private List<String> args;

    public JavaCompilerArgumentsBuilder(JavaCompileSpec spec) {
        this.spec = spec;
    }

    public JavaCompilerArgumentsBuilder includeLauncherOptions(boolean flag) {
        includeLauncherOptions = flag;
        return this;
    }

    public JavaCompilerArgumentsBuilder includeMainOptions(boolean flag) {
        includeMainOptions = flag;
        return this;
    }

    public JavaCompilerArgumentsBuilder includeClasspath(boolean flag) {
        includeClasspath = flag;
        return this;
    }

    public JavaCompilerArgumentsBuilder includeSourceFiles(boolean flag) {
        includeSourceFiles = flag;
        return this;
    }

    public List<String> build() {
        args = new ArrayList<String>();

        addLauncherOptions();
        addMainOptions();
        addClasspath();
        addSourceFiles();

        return args;
    }

    private void addLauncherOptions() {
        if (!includeLauncherOptions) { return; }

        ForkOptions forkOptions = spec.getCompileOptions().getForkOptions();
        if (forkOptions.getMemoryInitialSize() != null) {
            args.add("-J-Xms" + forkOptions.getMemoryInitialSize().trim());
        }
        if (forkOptions.getMemoryMaximumSize() != null) {
            args.add("-J-Xmx" + forkOptions.getMemoryMaximumSize().trim());
        }
        if (forkOptions.getJvmArgs() != null) {
            args.addAll(forkOptions.getJvmArgs());
        }
    }

    private void addMainOptions() {
        if (!includeMainOptions) { return; }

        String sourceCompatibility = spec.getSourceCompatibility();
        if (sourceCompatibility != null && !JavaVersion.current().equals(JavaVersion.toVersion(sourceCompatibility))) {
            args.add("-source");
            args.add(sourceCompatibility);
        }
        String targetCompatibility = spec.getTargetCompatibility();
        if (targetCompatibility != null && !JavaVersion.current().equals(JavaVersion.toVersion(targetCompatibility))) {
            args.add("-target");
            args.add(targetCompatibility);
        }
        File destinationDir = spec.getDestinationDir();
        if (destinationDir != null) {
            args.add("-d");
            args.add(destinationDir.getPath());
        }
        CompileOptions compileOptions = spec.getCompileOptions();
        if (compileOptions.isVerbose()) {
            args.add("-verbose");
        }
        if (compileOptions.isDeprecation()) {
            args.add("-deprecation");
        }
        if (!compileOptions.isWarnings()) {
            args.add("-nowarn");
        }
        if (compileOptions.isDebug()) {
            if (compileOptions.getDebugOptions().getDebugLevel() != null) {
                args.add("-g:" + compileOptions.getDebugOptions().getDebugLevel().trim());
            } else {
                args.add("-g");
            }
        } else {
            args.add("-g:none");
        }
        if (compileOptions.getEncoding() != null) {
            args.add("-encoding");
            args.add(compileOptions.getEncoding());
        }
        if (compileOptions.getBootClasspath() != null) {
            args.add("-bootclasspath");
            args.add(compileOptions.getBootClasspath());
        }
        if (compileOptions.getExtensionDirs() != null) {
            args.add("-extdirs");
            args.add(compileOptions.getExtensionDirs());
        }
        if (compileOptions.getCompilerArgs() != null) {
            args.addAll(compileOptions.getCompilerArgs());
        }
    }

    private void addClasspath() {
        if (!includeClasspath) { return; }

        Iterable<File> classpath = spec.getClasspath();
        if (classpath != null && classpath.iterator().hasNext()) {
            args.add("-classpath");
            args.add(toFileCollection(classpath).getAsPath());
        }
    }

    private void addSourceFiles() {
        if (!includeSourceFiles) { return; }

        for (File file : spec.getSource()) {
            args.add(file.getPath());
        }
    }

    private FileCollection toFileCollection(Iterable<File> classpath) {
        if (classpath instanceof FileCollection) {
            return (FileCollection) classpath;
        }
        return new SimpleFileCollection(Lists.newArrayList(classpath));
    }
}
