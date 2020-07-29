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

import org.gradle.api.Action;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.file.FileCollectionFactory;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.jvm.ModularitySpec;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;
import org.gradle.initialization.BuildCancellationToken;
import org.gradle.internal.jvm.DefaultModularitySpec;
import org.gradle.internal.jvm.JavaModuleDetector;
import org.gradle.process.CommandLineArgumentProvider;
import org.gradle.process.JavaDebugOptions;
import org.gradle.process.JavaExecSpec;
import org.gradle.process.JavaForkOptions;
import org.gradle.util.CollectionUtils;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.jar.Attributes;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;

import static org.gradle.process.internal.util.LongCommandLineDetectionUtil.hasCommandLineExceedMaxLength;

/**
 * Use {@link JavaExecHandleFactory} instead.
 */
public class JavaExecHandleBuilder extends AbstractExecHandleBuilder implements JavaExecSpec, ProcessArgumentsSpec.HasExecutable {
    private static final Logger LOGGER = Logging.getLogger(JavaExecHandleBuilder.class);
    private final FileCollectionFactory fileCollectionFactory;
    private final JavaModuleDetector javaModuleDetector;
    private final Property<String> mainModule;
    private final Property<String> mainClass;
    private ConfigurableFileCollection classpath;
    private final JavaForkOptions javaOptions;
    private final ProcessArgumentsSpec applicationArgsSpec = new ProcessArgumentsSpec(this);
    private final ModularitySpec modularity;

    public JavaExecHandleBuilder(FileResolver fileResolver, FileCollectionFactory fileCollectionFactory, ObjectFactory objectFactory, Executor executor, BuildCancellationToken buildCancellationToken, @Nullable JavaModuleDetector javaModuleDetector, JavaForkOptions javaOptions) {
        super(fileResolver, executor, buildCancellationToken);
        this.fileCollectionFactory = fileCollectionFactory;
        this.javaModuleDetector = javaModuleDetector;
        this.classpath = fileCollectionFactory.configurableFiles("classpath");
        this.mainModule = objectFactory.property(String.class);
        this.mainClass = objectFactory.property(String.class);
        this.javaOptions = javaOptions;
        this.modularity = new DefaultModularitySpec(objectFactory);
        executable(javaOptions.getExecutable());
    }

    @Override
    public List<String> getAllJvmArgs() {
        return getAllJvmArgs(this.classpath);
    }

    private List<String> getAllJvmArgs(FileCollection realClasspath) {
        List<String> allArgs = new ArrayList<>(javaOptions.getAllJvmArgs());
        boolean runAsModule = modularity.getInferModulePath().get() && mainModule.isPresent();

        if (runAsModule) {
            addModularJavaRunArgs(realClasspath, allArgs);
        } else {
            addClassicJavaRunArgs(realClasspath, allArgs);
        }

        return allArgs;
    }

    private void addClassicJavaRunArgs(FileCollection classpath, List<String> allArgs) {
        if (!mainClass.isPresent()) {
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
            allArgs.add(mainClass.get());
        }
    }

    private void addModularJavaRunArgs(FileCollection classpath, List<String> allArgs) {
        if (javaModuleDetector == null) {
            throw new IllegalStateException("Running a Java module is not supported in this context.");
        }
        FileCollection rtModulePath = javaModuleDetector.inferModulePath(modularity.getInferModulePath().get(), classpath);
        FileCollection rtClasspath = javaModuleDetector.inferClasspath(modularity.getInferModulePath().get(), classpath);

        if (rtClasspath != null && !rtClasspath.isEmpty()) {
            allArgs.add("-cp");
            allArgs.add(CollectionUtils.join(File.pathSeparator, rtClasspath));
        }
        if (rtModulePath != null && !rtModulePath.isEmpty()) {
            allArgs.add("--module-path");
            allArgs.add(CollectionUtils.join(File.pathSeparator, rtModulePath));
        }
        allArgs.add("--module");
        if (!mainClass.isPresent()) {
            allArgs.add(mainModule.get());
        } else {
            allArgs.add(mainModule.get() + "/" + mainClass.get());
        }
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
    public Property<String> getMainModule() {
        return mainModule;
    }

    @Override
    public Property<String> getMainClass() {
        return mainClass;
    }

    @Override
    public String getMain() {
        return mainClass.getOrNull();
    }

    @Override
    public JavaExecHandleBuilder setMain(String mainClassName) {
        this.mainClass.set(mainClassName);
        return this;
    }

    @Override
    @Nonnull
    public List<String> getArgs() {
        return applicationArgsSpec.getArgs();
    }

    @Override
    public JavaExecHandleBuilder setArgs(List<String> applicationArgs) {
        applicationArgsSpec.setArgs(applicationArgs);
        return this;
    }

    @Override
    public JavaExecHandleBuilder setArgs(Iterable<?> applicationArgs) {
        applicationArgsSpec.setArgs(applicationArgs);
        return this;
    }

    @Override
    public JavaExecHandleBuilder args(Object... args) {
        applicationArgsSpec.args(args);
        return this;
    }

    @Override
    public JavaExecSpec args(Iterable<?> args) {
        applicationArgsSpec.args(args);
        return this;
    }

    @Override
    public List<CommandLineArgumentProvider> getArgumentProviders() {
        return applicationArgsSpec.getArgumentProviders();
    }

    @Override
    public JavaExecHandleBuilder setClasspath(FileCollection classpath) {
        // we need to create a new file collection container to avoid cycles. See: https://github.com/gradle/gradle/issues/8755
        ConfigurableFileCollection newClasspath = fileCollectionFactory.configurableFiles("classpath");
        newClasspath.setFrom(classpath);
        this.classpath = newClasspath;
        return this;
    }

    @Override
    public ModularitySpec getModularity() {
        return modularity;
    }

    @Override
    public JavaExecHandleBuilder classpath(Object... paths) {
        this.classpath.from(paths);
        return this;
    }

    @Override
    public FileCollection getClasspath() {
        return classpath;
    }

    @Override
    public List<String> getAllArguments() {
        return getAllArguments(this.classpath);
    }

    private List<String> getAllArguments(FileCollection realClasspath) {
        List<String> arguments = new ArrayList<>(getAllJvmArgs(realClasspath));
        arguments.addAll(applicationArgsSpec.getAllArguments());
        return arguments;
    }

    @Override
    protected List<String> getEffectiveArguments() {
        List<String> arguments = getAllArguments();

        // Try to shorten command-line if necessary
        if (hasCommandLineExceedMaxLength(getExecutable(), arguments)) {
            try {
                File pathingJarFile = writePathingJarFile(classpath);
                ConfigurableFileCollection shortenedClasspath = fileCollectionFactory.configurableFiles();
                shortenedClasspath.from(pathingJarFile);
                List<String> shortenedArguments = getAllArguments(shortenedClasspath);
                LOGGER.info("Shortening Java classpath {} with {}", this.classpath.getFiles(), pathingJarFile);
                return shortenedArguments;
            } catch (IOException e) {
                LOGGER.info("Pathing JAR could not be created, Gradle cannot shorten the command line.", e);
            }
        }

        return arguments;
    }

    private File writePathingJarFile(FileCollection classPath) throws IOException {
        File pathingJarFile = File.createTempFile("gradle-javaexec-classpath", ".jar");
        try (FileOutputStream fileOutputStream = new FileOutputStream(pathingJarFile);
             JarOutputStream jarOutputStream = new JarOutputStream(fileOutputStream, toManifest(classPath))) {
            jarOutputStream.putNextEntry(new ZipEntry("META-INF/"));
        }
        return pathingJarFile;
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
