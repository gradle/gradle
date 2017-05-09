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
import org.gradle.api.JavaVersion;
import org.gradle.api.file.FileCollection;
import org.gradle.api.tasks.compile.CompileOptions;
import org.gradle.api.tasks.compile.ForkOptions;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class JavaCompilerArgumentsBuilder {
    public static final String USE_UNSHARED_COMPILER_TABLE_OPTION = "-XDuseUnsharedTable=true";
    public static final String EMPTY_SOURCE_PATH_REF_DIR = "emptySourcePathRef";

    private final JavaCompileSpec spec;

    private boolean includeLauncherOptions;
    private boolean includeMainOptions = true;
    private boolean includeClasspath = true;
    private boolean includeSourceFiles;
    private boolean noEmptySourcePath;

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

    public JavaCompilerArgumentsBuilder noEmptySourcePath() {
        noEmptySourcePath = true;
        return this;
    }

    public List<String> build() {
        args = new ArrayList<String>();

        addLauncherOptions();
        addMainOptions();
        addClasspath();
        addUserProvidedArgs();
        addSourceFiles();

        return args;
    }

    private void addLauncherOptions() {
        if (!includeLauncherOptions) {
            return;
        }

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
        if (!includeMainOptions) {
            return;
        }

        CompileOptions compileOptions = spec.getCompileOptions();
        List<String> compilerArgs = compileOptions.getCompilerArgs();
        if (!releaseOptionIsSet(compilerArgs)) {
            String sourceCompatibility = spec.getSourceCompatibility();
            if (sourceCompatibility != null) {
                args.add("-source");
                args.add(sourceCompatibility);
            }
            String targetCompatibility = spec.getTargetCompatibility();
            if (targetCompatibility != null) {
                args.add("-target");
                args.add(targetCompatibility);
            }
        }
        File destinationDir = spec.getDestinationDir();
        if (destinationDir != null) {
            args.add("-d");
            args.add(destinationDir.getPath());
        }
        if (compileOptions.isVerbose()) {
            args.add("-verbose");
        }
        if (compileOptions.isDeprecation()) {
            args.add("-deprecation");
        }
        if (!compileOptions.isWarnings()) {
            args.add("-nowarn");
        }
        if (compileOptions.getEncoding() != null) {
            args.add("-encoding");
            args.add(compileOptions.getEncoding());
        }
        if (compileOptions.getBootClasspath() != null) { //TODO: move bootclasspath to platform
            args.add("-bootclasspath");
            args.add(compileOptions.getBootClasspath());
        }
        if (compileOptions.getExtensionDirs() != null) {
            args.add("-extdirs");
            args.add(compileOptions.getExtensionDirs());
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

        FileCollection sourcepath = compileOptions.getSourcepath();
        if (!noEmptySourcePath || sourcepath != null && sourcepath.isEmpty()) {
            args.add("-sourcepath");
            args.add(sourcepath == null ? "" : sourcepath.getAsPath());
        }

        if (spec.getSourceCompatibility() == null || JavaVersion.toVersion(spec.getSourceCompatibility()).compareTo(JavaVersion.VERSION_1_6) >= 0) {
            List<File> annotationProcessorPath = spec.getAnnotationProcessorPath();
            if (annotationProcessorPath == null || annotationProcessorPath.isEmpty()) {
                args.add("-proc:none");
            } else {
                args.add("-processorpath");
                args.add(Joiner.on(File.pathSeparator).join(annotationProcessorPath));
            }
        }

        /*This is an internal option, it's used in com.sun.tools.javac.util.Names#createTable(Options options). The -XD backdoor switch is used to set it, as described in a comment
        in com.sun.tools.javac.main.RecognizedOptions#getAll(OptionHelper helper). This option was introduced in JDK 7 and controls if compiler's name tables should be reused.
        Without this option being set they are stored in a static list using soft references which can lead to memory pressure and performance deterioration
        when using the daemon, especially when using small heap and building a large project.
        Due to a bug (https://builds.gradle.org/viewLog.html?buildId=284033&tab=buildResultsDiv&buildTypeId=Gradle_Master_Performance_PerformanceExperimentsLinux) no instances of
        SharedNameTable are actually ever reused. It has been fixed for JDK9 and we should consider not using this option with JDK9 as not using it  will quite probably improve the
        performance of compilation.
        Using this option leads to significant performance improvements when using daemon and compiling java sources with JDK7 and JDK8.*/
        args.add(USE_UNSHARED_COMPILER_TABLE_OPTION);
    }

    private void addUserProvidedArgs() {
        if (!includeMainOptions) {
            return;
        }

        List<String> compilerArgs = spec.getCompileOptions().getCompilerArgs();
        if (compilerArgs != null) {
            args.addAll(compilerArgs);
        }
    }

    private boolean releaseOptionIsSet(List<String> compilerArgs) {
        return compilerArgs != null && compilerArgs.contains("-release");
    }

    private void addClasspath() {
        if (!includeClasspath) {
            return;
        }

        List<File> classpath = spec.getCompileClasspath();
        args.add("-classpath");
        args.add(classpath == null ? "" : Joiner.on(File.pathSeparatorChar).join(classpath));
    }

    private void addSourceFiles() {
        if (!includeSourceFiles) {
            return;
        }

        for (File file : spec.getSource()) {
            args.add(file.getPath());
        }
    }
}
