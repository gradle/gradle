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
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.JavaVersion;
import org.gradle.internal.Cast;
import org.gradle.util.internal.GUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

public class JavaCompilerArgumentsBuilder {
    public static final Logger LOGGER = LoggerFactory.getLogger(JavaCompilerArgumentsBuilder.class);
    public static final String USE_UNSHARED_COMPILER_TABLE_OPTION = "-XDuseUnsharedTable=true";
    public static final String EMPTY_SOURCE_PATH_REF_DIR = "emptySourcePathRef";

    private final JavaCompileSpec spec;

    private boolean includeLauncherOptions;
    private boolean includeMainOptions = true;
    private boolean includeClasspath = true;
    private boolean includeSourceFiles;
    private boolean allowEmptySourcePath = true;

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
        allowEmptySourcePath = false;
        return this;
    }

    /**
     * Returns a list with all Java compiler arguments as configured in this builder.
     * Returned arguments are guaranteed not to be null.
     *
     * @return a list containing all Java compiler arguments
     */
    public List<String> build() {
        args = new ArrayList<>();
        // Take a deep copy of the compilerArgs because the following methods mutate it.
        List<Object> compilerArgs = Cast.uncheckedCast(spec.getCompileOptions().getCompilerArgs());
        List<String> compArgs = compilerArgs
            .stream()
            .map(Object::toString)
            .collect(Collectors.toList());

        validateCompilerArgs(compArgs);

        addLauncherOptions();
        addMainOptions(compArgs);
        addClasspath();
        addUserProvidedArgs(compArgs);
        addSourceFiles();

        return args;
    }

    private void validateCompilerArgs(List<String> compilerArgs) {
        for (String arg : compilerArgs) {
            if ("-sourcepath".equals(arg) || "--source-path".equals(arg)) {
                throw new InvalidUserDataException("Cannot specify -sourcepath or --source-path via `CompileOptions.compilerArgs`. " +
                    "Use the `CompileOptions.sourcepath` property instead.");
            }

            if ("-processorpath".equals(arg) || "--processor-path".equals(arg)) {
                throw new InvalidUserDataException("Cannot specify -processorpath or --processor-path via `CompileOptions.compilerArgs`. " +
                    "Use the `CompileOptions.annotationProcessorPath` property instead.");
            }

            if (arg != null && arg.startsWith("-J")) {
                throw new InvalidUserDataException("Cannot specify -J flags via `CompileOptions.compilerArgs`. " +
                    "Use the `CompileOptions.forkOptions.jvmArgs` property instead.");
            }

            if ("--release".equals(arg) && spec.getRelease() != null) {
                throw new InvalidUserDataException("Cannot specify --release via `CompileOptions.compilerArgs` when using `CompileOptions.release`.");
            }

            if ("--enable-preview".equals(arg) && spec.getEnablePreview() != null) {
                throw new InvalidUserDataException("Cannot specify --enable-preview via `CompileOptions.compilerArgs` when using `CompileOptions.enablePreview`.");
            }
        }
    }

    private void addLauncherOptions() {
        if (!includeLauncherOptions) {
            return;
        }

        MinimalJavaCompilerDaemonForkOptions forkOptions = spec.getCompileOptions().getForkOptions();
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

    private void addMainOptions(List<String> compilerArgs) {
        if (!includeMainOptions) {
            return;
        }
        Integer release = spec.getRelease();
        final MinimalJavaCompileOptions compileOptions = spec.getCompileOptions();

        if (spec.getEnablePreview() != null && spec.getEnablePreview()) {
            args.add("--enable-preview");
        }

        if (release != null) {
            args.add("--release");
            args.add(release.toString());
        } else if (!releaseOptionIsSet(compilerArgs)) {
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
        String bootClasspath = compileOptions.getBootClasspath();
        if (bootClasspath != null) { //TODO: move bootclasspath to platform
            args.add("-bootclasspath");
            args.add(bootClasspath);
        }
        if (compileOptions.getExtensionDirs() != null) {
            args.add("-extdirs");
            args.add(compileOptions.getExtensionDirs());
        }
        if (compileOptions.getHeaderOutputDirectory() != null) {
            args.add("-h");
            args.add(compileOptions.getHeaderOutputDirectory().getPath());
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

        addSourcePathArg(compilerArgs, compileOptions);

        if (spec.getSourceCompatibility() == null || JavaVersion.toVersion(spec.getSourceCompatibility()).compareTo(JavaVersion.VERSION_1_6) >= 0) {
            List<File> annotationProcessorPath = spec.getAnnotationProcessorPath();
            if (annotationProcessorPath == null || annotationProcessorPath.isEmpty()) {
                args.add("-proc:none");
            } else {
                args.add("-processorpath");
                args.add(Joiner.on(File.pathSeparator).join(annotationProcessorPath));
            }
            if (compileOptions.getAnnotationProcessorGeneratedSourcesDirectory() != null) {
                args.add("-s");
                args.add(compileOptions.getAnnotationProcessorGeneratedSourcesDirectory().getPath());
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

    private void addSourcePathArg(List<String> compilerArgs, MinimalJavaCompileOptions compileOptions) {
        Collection<File> sourcepath = compileOptions.getSourcepath();
        boolean emptySourcePath = sourcepath == null || sourcepath.isEmpty();

        if (compilerArgs.contains("--module-source-path")) {
            if (!emptySourcePath) {
                LOGGER.warn("You specified both --module-source-path and a sourcepath. These options are mutually exclusive. Ignoring sourcepath.");
            }
            return;
        }

        if (emptySourcePath) {
            if (allowEmptySourcePath) {
                args.add("-sourcepath");
                args.add("");
            }
            return;
        }

        args.add("-sourcepath");
        args.add(GUtil.asPath(sourcepath));
    }

    private void addUserProvidedArgs(List<String> compilerArgs) {
        if (!includeMainOptions) {
            return;
        }
        if (compilerArgs != null) {
            args.addAll(compilerArgs);
        }
    }

    private boolean releaseOptionIsSet(List<String> compilerArgs) {
        return compilerArgs != null && compilerArgs.contains("--release");
    }

    private void addClasspath() {
        if (!includeClasspath) {
            return;
        }

        List<File> classpath = spec.getCompileClasspath();
        List<File> modulePath = spec.getModulePath();
        String moduleVersion = spec.getCompileOptions().getJavaModuleVersion();

        // Even if `classpath` is empty, we still need to pass `-classpath ""` to the compiler.
        // Otherwise, the compiler will try to infer the classpath by looking at the `java.class.path` system property.
        args.add("-classpath");
        args.add(Joiner.on(File.pathSeparatorChar).join(classpath));

        if (!modulePath.isEmpty()) {
            if (moduleVersion != null) {
                args.add("--module-version");
                args.add(moduleVersion);
            }
            args.add("--module-path");
            args.add(Joiner.on(File.pathSeparatorChar).join(modulePath));
        }
    }

    private void addSourceFiles() {
        if (!includeSourceFiles) {
            return;
        }

        for (File file : spec.getSourceFiles()) {
            args.add(file.getPath());
        }
    }
}
