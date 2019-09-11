/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.process.internal;

import com.google.common.collect.Iterables;
import org.gradle.api.Action;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.file.FileCollectionFactory;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.initialization.BuildCancellationToken;
import org.gradle.process.CommandLineArgumentProvider;
import org.gradle.process.JavaDebugOptions;
import org.gradle.process.JavaExecSpec;
import org.gradle.process.JavaForkOptions;
import org.gradle.util.CollectionUtils;
import org.gradle.util.GUtil;

import javax.annotation.Nonnull;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.jar.Attributes;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;

import static org.gradle.process.internal.util.LongCommandLineDetectionUtil.hasCommandLineExceedMaxLength;
import static org.gradle.process.internal.util.LongCommandLineDetectionUtil.hasEnvironmentVariableExceedMaxLength;

/**
 * Use {@link JavaExecHandleFactory} instead.
 */
public class JavaExecHandleBuilder extends AbstractExecHandleBuilder implements JavaExecSpec {
    private static final Logger LOGGER = Logging.getLogger(JavaExecHandleBuilder.class);
    private final FileCollectionFactory fileCollectionFactory;
    private String mainClass;
    private final List<Object> applicationArgs = new ArrayList<Object>();
    private ConfigurableFileCollection classpath;
    private final JavaForkOptions javaOptions;
    private final List<CommandLineArgumentProvider> argumentProviders = new ArrayList<CommandLineArgumentProvider>();

    public JavaExecHandleBuilder(FileResolver fileResolver, FileCollectionFactory fileCollectionFactory, Executor executor, BuildCancellationToken buildCancellationToken, JavaForkOptions javaOptions) {
        super(fileResolver, executor, buildCancellationToken);
        this.fileCollectionFactory = fileCollectionFactory;
        this.javaOptions = javaOptions;
        executable(javaOptions.getExecutable());

    }

    @Override
    public List<String> getAllJvmArgs() {
        List<String> allArgs = new ArrayList<String>(javaOptions.getAllJvmArgs());
        if (mainClass == null) {
            if (classpath != null && classpath.getFiles().size() == 1) {
                allArgs.add("-jar");
                allArgs.add(classpath.getSingleFile().getAbsolutePath());
            } else {
                throw new IllegalStateException("No main class specified and classpath is not an executable jar.");
            }
        } else {
            if (classpath != null && !classpath.isEmpty()) {
                allArgs.add("-cp");
                allArgs.add(CollectionUtils.join(File.pathSeparator, classpath));
            }
            allArgs.add(mainClass);
        }
        return allArgs;
    }

    @Override
    public void setAllJvmArgs(List<String> arguments) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setAllJvmArgs(Iterable<?> arguments) {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<String> getJvmArgs() {
        return javaOptions.getJvmArgs();
    }

    @Override
    public void setJvmArgs(List<String> arguments) {
        javaOptions.setJvmArgs(arguments);
    }

    @Override
    public void setJvmArgs(Iterable<?> arguments) {
        javaOptions.setJvmArgs(arguments);
    }

    @Override
    public JavaExecHandleBuilder jvmArgs(Iterable<?> arguments) {
        javaOptions.jvmArgs(arguments);
        return this;
    }

    @Override
    public JavaExecHandleBuilder jvmArgs(Object... arguments) {
        javaOptions.jvmArgs(arguments);
        return this;
    }

    @Override
    public Map<String, Object> getSystemProperties() {
        return javaOptions.getSystemProperties();
    }

    @Override
    public void setSystemProperties(Map<String, ?> properties) {
        javaOptions.setSystemProperties(properties);
    }

    @Override
    public JavaExecHandleBuilder systemProperties(Map<String, ?> properties) {
        javaOptions.systemProperties(properties);
        return this;
    }

    @Override
    public JavaExecHandleBuilder systemProperty(String name, Object value) {
        javaOptions.systemProperty(name, value);
        return this;
    }

    @Override
    public FileCollection getBootstrapClasspath() {
        return javaOptions.getBootstrapClasspath();
    }

    @Override
    public void setBootstrapClasspath(FileCollection classpath) {
        javaOptions.setBootstrapClasspath(classpath);
    }

    @Override
    public JavaForkOptions bootstrapClasspath(Object... classpath) {
        javaOptions.bootstrapClasspath(classpath);
        return this;
    }

    @Override
    public String getMinHeapSize() {
        return javaOptions.getMinHeapSize();
    }

    @Override
    public void setMinHeapSize(String heapSize) {
        javaOptions.setMinHeapSize(heapSize);
    }

    @Override
    public String getDefaultCharacterEncoding() {
        return javaOptions.getDefaultCharacterEncoding();
    }

    @Override
    public void setDefaultCharacterEncoding(String defaultCharacterEncoding) {
        javaOptions.setDefaultCharacterEncoding(defaultCharacterEncoding);
    }

    @Override
    public String getMaxHeapSize() {
        return javaOptions.getMaxHeapSize();
    }

    @Override
    public void setMaxHeapSize(String heapSize) {
        javaOptions.setMaxHeapSize(heapSize);
    }

    @Override
    public boolean getEnableAssertions() {
        return javaOptions.getEnableAssertions();
    }

    @Override
    public void setEnableAssertions(boolean enabled) {
        javaOptions.setEnableAssertions(enabled);
    }

    @Override
    public boolean getDebug() {
        return javaOptions.getDebug();
    }

    @Override
    public void setDebug(boolean enabled) {
        javaOptions.setDebug(enabled);
    }

    @Override
    public JavaDebugOptions getDebugOptions() {
        return javaOptions.getDebugOptions();
    }

    @Override
    public void debugOptions(Action<JavaDebugOptions> action) {
        javaOptions.debugOptions(action);
    }

    @Override
    public String getMain() {
        return mainClass;
    }

    @Override
    public JavaExecHandleBuilder setMain(String mainClassName) {
        this.mainClass = mainClassName;
        return this;
    }

    @Override
    @Nonnull
    public List<String> getArgs() {
        List<String> args = new ArrayList<String>();
        for (Object applicationArg : applicationArgs) {
            args.add(applicationArg.toString());
        }
        return args;
    }

    @Override
    public JavaExecHandleBuilder setArgs(List<String> applicationArgs) {
        this.applicationArgs.clear();
        args(applicationArgs);
        return this;
    }

    @Override
    public JavaExecHandleBuilder setArgs(Iterable<?> applicationArgs) {
        this.applicationArgs.clear();
        args(applicationArgs);
        return this;
    }

    @Override
    public JavaExecHandleBuilder args(Object... args) {
        args(Arrays.asList(args));
        return this;
    }

    @Override
    public JavaExecSpec args(Iterable<?> args) {
        GUtil.addToCollection(applicationArgs, true, args);
        return this;
    }

    @Override
    public List<CommandLineArgumentProvider> getArgumentProviders() {
        return argumentProviders;
    }

    @Override
    public JavaExecHandleBuilder setClasspath(FileCollection classpath) {
        ConfigurableFileCollection newClasspath = fileCollectionFactory.configurableFiles("classpath");
        newClasspath.setFrom(classpath);
        this.classpath = newClasspath;
        return this;
    }

    @Override
    public JavaExecHandleBuilder classpath(Object... paths) {
        doGetClasspath().from(paths);
        return this;
    }

    @Override
    public FileCollection getClasspath() {
        return doGetClasspath();
    }

    private ConfigurableFileCollection doGetClasspath() {
        if (classpath == null) {
            classpath = fileCollectionFactory.configurableFiles("classpath");
        }
        return classpath;
    }

    @Override
    public List<String> getAllArguments() {
        List<String> arguments = new ArrayList<String>(getAllJvmArgs());
        arguments.addAll(getArgs());
        for (CommandLineArgumentProvider argumentProvider : argumentProviders) {
            Iterables.addAll(arguments, argumentProvider.asArguments());
        }
        return arguments;
    }

    @Override
    protected List<String> getEffectiveArguments() {
        List<String> arguments = getAllArguments();

        if (hasCommandLineExceedMaxLength(getExecutable(), arguments)) {
            int classPathFlagIndex = arguments.indexOf("-cp");
            if (shouldTryShorteningCommandLine(arguments)) {
                try {
                    File pathingJarFile = File.createTempFile("gradle-javaexec-classpath", ".jar");
                    List<String> jvmArgs = writePathingJarFile(classpath, pathingJarFile);
                    arguments.remove(classPathFlagIndex);
                    arguments.remove(classPathFlagIndex);
                    arguments.addAll(classPathFlagIndex, jvmArgs);
                    LOGGER.info("Shortening Java classpath {} with {}", this.classpath, pathingJarFile);
                } catch (IOException e) {
                    LOGGER.info("Pathing JAR could not be created, Gradle cannot shorten the command line.");
                }
            }
        }

        return arguments;
    }

    private boolean shouldTryShorteningCommandLine(List<String> arguments) {
        int classPathFlagIndex = arguments.indexOf("-cp");
        if (classPathFlagIndex == -1) {
            // No class path flag, so we can't use CLASSPATH
            LOGGER.info("No class path flag found in the command line, Gradle cannot shorten the command line");
            return false;
        }

        return true;
    }

    private List<String> writePathingJarFile(FileCollection classPath, File pathingJarFile) throws IOException {
        try (JarOutputStream jarOutputStream = new JarOutputStream(new FileOutputStream(pathingJarFile), toManifest(classPath))) {
            jarOutputStream.putNextEntry(new ZipEntry("META-INF/"));
        }
        return Arrays.asList("-cp", pathingJarFile.getAbsolutePath());
    }

    private static Manifest toManifest(FileCollection classPath) {
        Manifest manifest = new Manifest();
        Attributes attributes = manifest.getMainAttributes();
        attributes.put(Attributes.Name.MANIFEST_VERSION, "1.0");
        attributes.putValue("Class-Path", classPath.getFiles().stream().map(File::toURI).map(URI::toString).collect(Collectors.joining(" ")));
        return manifest;
    }

    @Override
    public JavaForkOptions copyTo(JavaForkOptions options) {
        throw new UnsupportedOperationException();
    }

    @Override
    public JavaExecHandleBuilder setIgnoreExitValue(boolean ignoreExitValue) {
        super.setIgnoreExitValue(ignoreExitValue);
        return this;
    }

    @Override
    public List<CommandLineArgumentProvider> getJvmArgumentProviders() {
        return javaOptions.getJvmArgumentProviders();
    }
}
