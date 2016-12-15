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

import org.gradle.api.UncheckedIOException;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.classpath.ManifestUtil;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.internal.file.collections.DefaultConfigurableFileCollection;
import org.gradle.internal.os.OperatingSystem;
import org.gradle.process.JavaExecSpec;
import org.gradle.process.JavaForkOptions;
import org.gradle.util.CollectionUtils;
import org.gradle.util.GUtil;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.jar.Attributes;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

public class JavaExecHandleBuilder extends AbstractExecHandleBuilder implements JavaExecSpec {
    private String mainClass;
    private final List<Object> applicationArgs = new ArrayList<Object>();
    private FileCollection classpath;
    private final JavaForkOptions javaOptions;
    private final FileResolver fileResolver;
    private Boolean manifestOnlyJar;

    public JavaExecHandleBuilder(FileResolver fileResolver) {
        super(fileResolver);
        this.fileResolver = fileResolver;
        javaOptions = new DefaultJavaForkOptions(fileResolver);
        classpath = new DefaultConfigurableFileCollection(fileResolver, null);
        executable(javaOptions.getExecutable());
    }

    public List<String> getAllJvmArgs() {
        List<String> allArgs = new ArrayList<String>();
        allArgs.addAll(javaOptions.getAllJvmArgs());
        if (!classpath.isEmpty() && !useManifestOnlyJar()) {
            allArgs.add("-cp");
            allArgs.add(CollectionUtils.join(File.pathSeparator, classpath.getFiles()));
        }
        return allArgs;
    }

    public void setAllJvmArgs(Iterable<?> arguments) {
        throw new UnsupportedOperationException();
    }

    public List<String> getJvmArgs() {
        return javaOptions.getJvmArgs();
    }

    public void setJvmArgs(Iterable<?> arguments) {
        javaOptions.setJvmArgs(arguments);
    }

    public JavaExecHandleBuilder jvmArgs(Iterable<?> arguments) {
        javaOptions.jvmArgs(arguments);
        return this;
    }

    public JavaExecHandleBuilder jvmArgs(Object... arguments) {
        javaOptions.jvmArgs(arguments);
        return this;
    }

    public Map<String, Object> getSystemProperties() {
        return javaOptions.getSystemProperties();
    }

    public void setSystemProperties(Map<String, ?> properties) {
        javaOptions.setSystemProperties(properties);
    }

    public JavaExecHandleBuilder systemProperties(Map<String, ?> properties) {
        javaOptions.systemProperties(properties);
        return this;
    }

    public JavaExecHandleBuilder systemProperty(String name, Object value) {
        javaOptions.systemProperty(name, value);
        return this;
    }

    public FileCollection getBootstrapClasspath() {
        return javaOptions.getBootstrapClasspath();
    }

    public void setBootstrapClasspath(FileCollection classpath) {
        javaOptions.setBootstrapClasspath(classpath);
    }

    public JavaForkOptions bootstrapClasspath(Object... classpath) {
        javaOptions.bootstrapClasspath(classpath);
        return this;
    }

    public String getMinHeapSize() {
        return javaOptions.getMinHeapSize();
    }

    public void setMinHeapSize(String heapSize) {
        javaOptions.setMinHeapSize(heapSize);
    }

    public String getDefaultCharacterEncoding() {
        return javaOptions.getDefaultCharacterEncoding();
    }

    public void setDefaultCharacterEncoding(String defaultCharacterEncoding) {
        javaOptions.setDefaultCharacterEncoding(defaultCharacterEncoding);
    }

    public String getMaxHeapSize() {
        return javaOptions.getMaxHeapSize();
    }

    public void setMaxHeapSize(String heapSize) {
        javaOptions.setMaxHeapSize(heapSize);
    }

    public boolean getEnableAssertions() {
        return javaOptions.getEnableAssertions();
    }

    public void setEnableAssertions(boolean enabled) {
        javaOptions.setEnableAssertions(enabled);
    }

    public boolean getDebug() {
        return javaOptions.getDebug();
    }

    public void setDebug(boolean enabled) {
        javaOptions.setDebug(enabled);
    }

    public String getMain() {
        return mainClass;
    }

    public JavaExecHandleBuilder setMain(String mainClassName) {
        this.mainClass = mainClassName;
        return this;
    }

    public List<String> getArgs() {
        List<String> args = new ArrayList<String>();
        for (Object applicationArg : applicationArgs) {
            args.add(applicationArg.toString());
        }
        return args;
    }

    public JavaExecHandleBuilder setArgs(Iterable<?> applicationArgs) {
        this.applicationArgs.clear();
        args(applicationArgs);
        return this;
    }

    public JavaExecHandleBuilder args(Object... args) {
        args(Arrays.asList(args));
        return this;
    }

    public JavaExecSpec args(Iterable<?> args) {
        GUtil.addToCollection(applicationArgs, true, args);
        return this;
    }

    public JavaExecHandleBuilder setClasspath(FileCollection classpath) {
        this.classpath = classpath;
        return this;
    }

    public JavaExecHandleBuilder classpath(Object... paths) {
        classpath = classpath.plus(fileResolver.resolveFiles(paths));
        return this;
    }

    public FileCollection getClasspath() {
        return classpath;
    }

    public void setManifestOnlyJar(Boolean useManifestOnlyJar) {
        this.manifestOnlyJar = useManifestOnlyJar;
    }

    @Override
    public List<String> getAllArguments() {
        List<String> arguments = new ArrayList<String>();
        arguments.addAll(getAllJvmArgs());
        if (useManifestOnlyJar()) {
            arguments.add("-jar");
            arguments.add(getManifestOnlyJar().toString());
        } else {
            arguments.add(mainClass);
        }
        arguments.addAll(getArgs());

        if (OperatingSystem.current().isWindows()
            &&
            manifestOnlyJar == null
            &&
            CollectionUtils.join(" ", arguments).length() > 30000) {
            manifestOnlyJar = true;
            return getAllArguments();
        }

        return arguments;
    }

    public JavaForkOptions copyTo(JavaForkOptions options) {
        throw new UnsupportedOperationException();
    }

    public ExecHandle build() {
        if (mainClass == null) {
            throw new IllegalStateException("No main class specified");
        }
        return super.build();
    }

    @Override
    public JavaExecHandleBuilder setIgnoreExitValue(boolean ignoreExitValue) {
        super.setIgnoreExitValue(ignoreExitValue);
        return this;
    }

    private boolean useManifestOnlyJar() {
        if (manifestOnlyJar != null) {
            return manifestOnlyJar;
        } else {
            return false;
        }
    }

    private File getManifestOnlyJar() {
        try {
            File jar = File.createTempFile("javaExec", ".jar");
            Manifest manifest = new Manifest();
            Attributes attributes = manifest.getMainAttributes();
            // nothing is written unless the Manifest-Version is set?
            attributes.putValue(Attributes.Name.MANIFEST_VERSION.toString(), "1.0");
            attributes.putValue(Attributes.Name.MAIN_CLASS.toString(), getMain());
            attributes.putValue(Attributes.Name.CLASS_PATH.toString(), ManifestUtil.createManifestClasspath(jar, classpath.getFiles()));
            new JarOutputStream(new FileOutputStream(jar), manifest).close();
            return jar;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
