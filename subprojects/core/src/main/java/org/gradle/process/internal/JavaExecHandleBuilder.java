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
import org.gradle.api.NonNullApi;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.file.FileCollectionFactory;
import org.gradle.api.internal.file.temp.TemporaryFileProvider;
import org.gradle.api.jvm.ModularitySpec;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.internal.jvm.DefaultModularitySpec;
import org.gradle.internal.jvm.JavaModuleDetector;
import org.gradle.process.BaseExecSpec;
import org.gradle.process.CommandLineArgumentProvider;
import org.gradle.process.JavaDebugOptions;
import org.gradle.process.JavaExecSpec;
import org.gradle.process.JavaForkOptions;
import org.gradle.process.internal.EffectiveJavaForkOptions.ReadOnlyJvmOptions;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.jar.Attributes;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;

import static org.gradle.process.internal.util.LongCommandLineDetectionUtil.hasCommandLineExceedMaxLength;

/**
 * Use {@link JavaExecHandleFactory} instead.
 */
@NonNullApi
public class JavaExecHandleBuilder implements BaseExecHandleBuilder {

    private static final Logger LOGGER = Logging.getLogger(JavaExecHandleBuilder.class);

    private final FileCollectionFactory fileCollectionFactory;
    private final TemporaryFileProvider temporaryFileProvider;
    private final JavaModuleDetector javaModuleDetector;
    private final Property<String> mainModule;
    private final Property<String> mainClass;
    private final ClientExecHandleBuilder execHandleBuilder;
    private ConfigurableFileCollection classpath;
    private final JavaForkOptionsInternal javaOptions;
    private final ModularitySpec modularity;

    public JavaExecHandleBuilder(
        FileCollectionFactory fileCollectionFactory,
        ObjectFactory objectFactory,
        TemporaryFileProvider temporaryFileProvider,
        @Nullable JavaModuleDetector javaModuleDetector,
        JavaForkOptionsInternal javaOptions,
        ClientExecHandleBuilder execHandleBuilder
    ) {
        this.fileCollectionFactory = fileCollectionFactory;
        this.temporaryFileProvider = temporaryFileProvider;
        this.javaModuleDetector = javaModuleDetector;
        this.classpath = fileCollectionFactory.configurableFiles("classpath");
        this.mainModule = objectFactory.property(String.class);
        this.mainClass = objectFactory.property(String.class);
        this.javaOptions = javaOptions;
        this.modularity = new DefaultModularitySpec(objectFactory);
        this.execHandleBuilder = execHandleBuilder;
    }

    public Provider<List<String>> getAllJvmArgs() {
        return getAllJvmArgs(this.classpath);
    }

    private Provider<List<String>> getAllJvmArgs(FileCollection realClasspath) {
        return javaOptions.getAllJvmArgs().map(allJvmArgs -> ExecHandleCommandLineCombiner.getAllJvmArgs(
            allJvmArgs,
            realClasspath,
            mainClass,
            mainModule,
            modularity,
            javaModuleDetector
        ));
    }

    public JavaExecHandleBuilder setExtraJvmArgs(Iterable<?> jvmArgs) {
        javaOptions.setExtraJvmArgs(jvmArgs);
        return this;
    }

    public JavaExecHandleBuilder jvmArgs(Iterable<?> arguments) {
        javaOptions.jvmArgs(arguments);
        return this;
    }

    public JavaExecHandleBuilder jvmArgs(Object... arguments) {
        javaOptions.jvmArgs(arguments);
        return this;
    }

    public MapProperty<String, Object> getSystemProperties() {
        return javaOptions.getSystemProperties();
    }

    public void setSystemProperties(Map<String, ?> properties) {
        javaOptions.getSystemProperties().set(properties);
    }

    public JavaExecHandleBuilder systemProperties(Map<String, ?> properties) {
        javaOptions.systemProperties(properties);
        return this;
    }

    public JavaExecHandleBuilder systemProperty(String name, Object value) {
        javaOptions.systemProperty(name, value);
        return this;
    }

    public ConfigurableFileCollection getBootstrapClasspath() {
        return javaOptions.getBootstrapClasspath();
    }


    public void setBootstrapClasspath(FileCollection classpath) {
        javaOptions.getBootstrapClasspath().setFrom(classpath);
    }

    public JavaExecHandleBuilder bootstrapClasspath(Object... classpath) {
        javaOptions.bootstrapClasspath(classpath);
        return this;
    }

    public Property<String> getMinHeapSize() {
        return javaOptions.getMinHeapSize();
    }

    public void setMinHeapSize(String heapSize) {
        javaOptions.getMinHeapSize().set(heapSize);
    }

    public Property<String> getDefaultCharacterEncoding() {
        return javaOptions.getDefaultCharacterEncoding();
    }

    public void setDefaultCharacterEncoding(String defaultCharacterEncoding) {
        javaOptions.getDefaultCharacterEncoding().set(defaultCharacterEncoding);
    }

    public Property<String> getMaxHeapSize() {
        return javaOptions.getMaxHeapSize();
    }

    public void setMaxHeapSize(String heapSize) {
        javaOptions.getMaxHeapSize().set(heapSize);
    }

    public void setEnableAssertions(boolean enabled) {
        javaOptions.getEnableAssertions().set(enabled);
    }

    public void setDebug(boolean enabled) {
        javaOptions.getDebug().set(enabled);
    }

    public void debugOptions(Action<JavaDebugOptions> action) {
        javaOptions.debugOptions(action);
    }

    public String getExecutable() {
        return javaOptions.getExecutable().get();
    }

    public void setExecutable(File executable) {
        setExecutable(executable.getAbsolutePath());
    }

    @Override
    public JavaExecHandleBuilder setExecutable(String executable) {
        javaOptions.getExecutable().set(executable);
        return this;
    }

    @Override
    public JavaExecHandleBuilder setWorkingDir(@Nullable File dir) {
        javaOptions.getWorkingDir().set(dir);
        return this;
    }

    @Override
    public JavaExecHandleBuilder setEnvironment(Map<String, Object> environmentVariables) {
        javaOptions.getEnvironment().set(environmentVariables);
        return this;
    }

    public JavaExecHandleBuilder environment(Map<String, ?> environmentVariables) {
        javaOptions.environment(environmentVariables);
        return this;
    }

    public JavaExecHandleBuilder environment(String name, Object value) {
        javaOptions.environment(name, value);
        return this;
    }

    public Property<String> getMainModule() {
        return mainModule;
    }

    public Property<String> getMainClass() {
        return mainClass;
    }

    @Nonnull
    public List<String> getArgs() {
        return execHandleBuilder.getArgs();
    }

    public JavaExecHandleBuilder setArgs(List<String> applicationArgs) {
        execHandleBuilder.setArgs(applicationArgs);
        return this;
    }

    public JavaExecHandleBuilder setArgs(Iterable<?> applicationArgs) {
        execHandleBuilder.setArgs(applicationArgs);
        return this;
    }

    public JavaExecHandleBuilder args(Object... args) {
        execHandleBuilder.args(args);
        return this;
    }

    public JavaExecHandleBuilder args(Iterable<?> args) {
        execHandleBuilder.args(args);
        return this;
    }

    public List<CommandLineArgumentProvider> getArgumentProviders() {
        return execHandleBuilder.getArgumentProviders();
    }

    public JavaExecHandleBuilder setArgumentProviders(List<CommandLineArgumentProvider> argumentProviders) {
        execHandleBuilder.setArgumentProviders(argumentProviders);
        return this;
    }

    public JavaExecHandleBuilder setClasspath(FileCollection classpath) {
        // we need to create a new file collection container to avoid cycles. See: https://github.com/gradle/gradle/issues/8755
        ConfigurableFileCollection newClasspath = fileCollectionFactory.configurableFiles("classpath");
        newClasspath.setFrom(classpath);
        this.classpath = newClasspath;
        return this;
    }

    public ModularitySpec getModularity() {
        return modularity;
    }

    public JavaExecHandleBuilder classpath(Object... paths) {
        this.classpath.from(paths);
        return this;
    }

    public FileCollection getClasspath() {
        return classpath;
    }

    public List<String> getAllArguments() {
        return getAllArguments(this.classpath);
    }

    private List<String> getAllArguments(FileCollection realClasspath) {
        return ExecHandleCommandLineCombiner.getAllArgs(getAllJvmArgs(realClasspath).get(), getArgs(), getArgumentProviders());
    }

    @Override
    public JavaExecHandleBuilder setStandardInput(InputStream inputStream) {
        execHandleBuilder.setStandardInput(inputStream);
        return this;
    }

    public InputStream getStandardInput() {
        return execHandleBuilder.getStandardInput();
    }

    public OutputStream getStandardOutput() {
        return execHandleBuilder.getStandardOutput();
    }

    @Override
    public JavaExecHandleBuilder setStandardOutput(OutputStream outputStream) {
        execHandleBuilder.setStandardOutput(outputStream);
        return this;
    }

    public OutputStream getErrorOutput() {
        return execHandleBuilder.getErrorOutput();
    }

    @Override
    public JavaExecHandleBuilder setErrorOutput(OutputStream outputStream) {
        execHandleBuilder.setErrorOutput(outputStream);
        return this;
    }

    public List<String> getCommandLine() {
        return ExecHandleCommandLineCombiner.getCommandLine(getExecutable(), getAllArguments());
    }

    @Override
    public JavaExecHandleBuilder listener(ExecHandleListener listener) {
        execHandleBuilder.listener(listener);
        return this;
    }

    @Override
    public JavaExecHandleBuilder setDisplayName(@Nullable String displayName) {
        execHandleBuilder.setDisplayName(displayName);
        return this;
    }

    private List<String> getEffectiveArguments() {
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
        File pathingJarFile = temporaryFileProvider.createTemporaryFile("gradle-javaexec-classpath", ".jar");
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

    public JavaExecHandleBuilder redirectErrorStream() {
        execHandleBuilder.redirectErrorStream();
        return this;
    }

    /**
     * Kept just for binary compatibility
     */
    @Deprecated
    public JavaExecHandleBuilder setIgnoreExitValue(boolean ignoreExitValue) {
        return this;
    }

    public void copyJavaForkOptions(JavaForkOptions source) {
        source.copyTo(javaOptions);
    }

    public void copyJavaForkOptions(ReadOnlyJvmOptions source) {
        source.copyTo(javaOptions);
    }

    @Nullable
    JavaModuleDetector getJavaModuleDetector() {
        return javaModuleDetector;
    }

    JavaExecHandleBuilder configureFrom(JavaExecSpec spec) {
        configureFrom((BaseExecSpec) spec);
        getMainModule().set(spec.getMainModule());
        getMainClass().set(spec.getMainClass());
        getModularity().getInferModulePath().set(spec.getModularity().getInferModulePath());
        classpath(spec.getClasspath());
        if (spec.getArgs() != null) {
            setArgs(spec.getArgs());
        }
        setArgumentProviders(spec.getArgumentProviders());
        copyJavaForkOptions(spec);
        return this;
    }

    @Override
    public ExecHandle build() {
        // We delegate properties that are also on ProcessForkOptions interface to JavaForkOptions
        // to support copy from JavaOptions, and thus we have to copy them to execHandleBuilder here
        execHandleBuilder.setExecutable(javaOptions.getExecutable().get());
        execHandleBuilder.setWorkingDir(javaOptions.getWorkingDir().getAsFile().get());
        execHandleBuilder.setEnvironment(javaOptions.getEnvironment().get());
        return execHandleBuilder.buildWithEffectiveArguments(getEffectiveArguments());
    }
}
