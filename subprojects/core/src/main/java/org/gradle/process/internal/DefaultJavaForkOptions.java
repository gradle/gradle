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
import org.gradle.api.internal.file.FileCollectionFactory;
import org.gradle.api.internal.provider.MapPropertyInternal;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.internal.file.PathToFileResolver;
import org.gradle.internal.jvm.Jvm;
import org.gradle.process.CommandLineArgumentProvider;
import org.gradle.process.JavaDebugOptions;
import org.gradle.process.JavaForkOptions;

import javax.inject.Inject;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class DefaultJavaForkOptions extends DefaultProcessForkOptions implements JavaForkOptionsInternal {
    private final FileCollectionFactory fileCollectionFactory;
    private final ObjectFactory objectFactory;
    private final ListProperty<String> jvmArgs;
    private final ListProperty<CommandLineArgumentProvider> jvmArgumentProviders;
    private final MapProperty<String, Object> systemProperties;
    private final ConfigurableFileCollection bootstrapClasspath;
    private final Property<String> minHeapSize;
    private final Property<String> maxHeapSize;
    private final Property<String> defaultCharacterEncoding;
    private final Property<Boolean> enableAssertions;
    private final JavaDebugOptions debugOptions;
    private Iterable<?> extraJvmArgs;

    @Inject
    public DefaultJavaForkOptions(
        ObjectFactory objectFactory,
        PathToFileResolver resolver,
        FileCollectionFactory fileCollectionFactory
    ) {
        super(resolver);
        this.objectFactory = objectFactory;
        this.fileCollectionFactory = fileCollectionFactory;
        this.jvmArgs = objectFactory.listProperty(String.class);
        this.jvmArgumentProviders = objectFactory.listProperty(CommandLineArgumentProvider.class);
        this.systemProperties = objectFactory.mapProperty(String.class, Object.class);
        this.bootstrapClasspath = fileCollectionFactory.configurableFiles();
        this.minHeapSize = objectFactory.property(String.class);
        this.maxHeapSize = objectFactory.property(String.class);
        this.debugOptions = objectFactory.newInstance(DefaultJavaDebugOptions.class, objectFactory);
        this.defaultCharacterEncoding = objectFactory.property(String.class);
        this.enableAssertions = objectFactory.property(Boolean.class);
    }

    @Override
    public Provider<List<String>> getAllJvmArgs() {
        return getJvmArgumentProviders().map(providers -> {
            JvmOptions copy = new JvmOptions(objectFactory, fileCollectionFactory);
            copy.copyFrom(this);
            return copy.getAllJvmArgs();
        });
    }

    @Override
    public ListProperty<String> getJvmArgs() {
        return jvmArgs;
    }

    @Override
    public JavaForkOptions jvmArgs(Iterable<?> arguments) {
        for (Object argument : arguments) {
            if (argument instanceof Provider) {
                getJvmArgs().add(((Provider<?>) argument).map(Object::toString));
            } else {
                getJvmArgs().add(argument.toString());
            }
        }
        return this;
    }

    @Override
    public JavaForkOptions jvmArgs(Object... arguments) {
        jvmArgs(Arrays.asList(arguments));
        return this;
    }

    @Override
    public ListProperty<CommandLineArgumentProvider> getJvmArgumentProviders() {
        return jvmArgumentProviders;
    }

    @Override
    public MapProperty<String, Object> getSystemProperties() {
        return systemProperties;
    }

    @Override
    public JavaForkOptions systemProperties(Map<String, ?> properties) {
        properties.forEach(this::systemProperty);
        return this;
    }

    @Override
    @SuppressWarnings("unchecked")
    public JavaForkOptions systemProperty(String name, Object value) {
        if (value instanceof Provider) {
            ((MapPropertyInternal<String, Object>) getSystemProperties()).insert(name, (Provider<?>) value);
        } else {
            getSystemProperties().put(name, value == null ? "" : value);
        }
        return this;
    }

    @Override
    public ConfigurableFileCollection getBootstrapClasspath() {
        return bootstrapClasspath;
    }

    @Override
    public JavaForkOptions bootstrapClasspath(Object... classpath) {
        getBootstrapClasspath().from(classpath);
        return this;
    }

    @Override
    public Property<String> getMinHeapSize() {
        return minHeapSize;
    }

    @Override
    public Property<String> getMaxHeapSize() {
        return maxHeapSize;
    }

    @Override
    public Property<String> getDefaultCharacterEncoding() {
        return defaultCharacterEncoding;
    }

    @Override
    public Property<Boolean> getEnableAssertions() {
        return enableAssertions;
    }

    @Override
    public Property<Boolean> getDebug() {
        return getDebugOptions().getEnabled();
    }

    @Override
    public JavaDebugOptions getDebugOptions() {
        return debugOptions;
    }

    @Override
    public void debugOptions(Action<JavaDebugOptions> action) {
        action.execute(debugOptions);
    }

    @Override
    protected Map<String, ?> getInheritableEnvironment() {
        // Filter out any environment variables that should not be inherited.
        return Jvm.getInheritableEnvironmentVariables(super.getInheritableEnvironment());
    }

    @Override
    public JavaForkOptions copyTo(JavaForkOptions target) {
        super.copyTo(target);
        target.getJvmArgs().set(getJvmArgs());
        target.getSystemProperties().set(getSystemProperties());
        target.getMinHeapSize().set(getMinHeapSize());
        target.getMaxHeapSize().set(getMaxHeapSize());
        target.bootstrapClasspath(getBootstrapClasspath());
        target.getEnableAssertions().set(getEnableAssertions());
        JvmOptions.copyDebugOptions(this.getDebugOptions(), target.getDebugOptions());
        target.getJvmArgumentProviders().set(getJvmArgumentProviders());
        return this;
    }

    @Override
    public void checkDebugConfiguration(Iterable<?> arguments) {
        AllJvmArgsAdapterUtil.checkDebugConfiguration(getDebugOptions(), arguments);
    }

    @Override
    public EffectiveJavaForkOptions toEffectiveJavaForkOptions(ObjectFactory objectFactory, FileCollectionFactory fileCollectionFactory) {
        JvmOptions copy = new JvmOptions(objectFactory, fileCollectionFactory);
        copy.copyFrom(this);
        return new EffectiveJavaForkOptions(
            getExecutable(),
            getWorkingDir(),
            getEnvironment(),
            copy
        );
    }

    @Override
    public void setExtraJvmArgs(Iterable<?> arguments) {
        this.extraJvmArgs = arguments;
    }

    @Override
    public Iterable<?> getExtraJvmArgs() {
        return extraJvmArgs;
    }
}
