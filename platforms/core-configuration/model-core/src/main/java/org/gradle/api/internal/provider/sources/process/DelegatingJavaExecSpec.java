/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.api.internal.provider.sources.process;

import org.gradle.api.Action;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.FileCollection;
import org.gradle.api.jvm.ModularitySpec;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.process.CommandLineArgumentProvider;
import org.gradle.process.JavaDebugOptions;
import org.gradle.process.JavaExecSpec;
import org.gradle.process.JavaForkOptions;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;

interface DelegatingJavaExecSpec extends DelegatingBaseExecSpec, JavaExecSpec {
    @Override
    default Property<String> getMainModule() {
        return getDelegate().getMainModule();
    }

    @Override
    default Property<String> getMainClass() {
        return getDelegate().getMainClass();
    }

    @Override
    default ListProperty<String> getArgs() {
        return getDelegate().getArgs();
    }

    @Override
    default JavaExecSpec setArgs(List<String> args) {
        getDelegate().setArgs(args);
        return this;
    }

    @Override
    default JavaExecSpec setArgs(Iterable<?> args) {
        getDelegate().setArgs(args);
        return this;
    }

    @Override
    default JavaExecSpec args(Object... args) {
        getDelegate().args(args);
        return this;
    }

    @Override
    default JavaExecSpec args(Iterable<?> args) {
        getDelegate().args(args);
        return this;
    }

    @Override
    default ListProperty<CommandLineArgumentProvider> getArgumentProviders() {
        return getDelegate().getArgumentProviders();
    }

    @Override
    default JavaExecSpec classpath(Object... paths) {
        getDelegate().classpath(paths);
        return this;
    }

    @Override
    default ConfigurableFileCollection getClasspath() {
        return getDelegate().getClasspath();
    }

    @Override
    default JavaExecSpec setClasspath(FileCollection classpath) {
        getDelegate().setClasspath(classpath);
        return this;
    }

    @Override
    default ModularitySpec getModularity() {
        return getDelegate().getModularity();
    }

    @Override
    default MapProperty<String, Object> getSystemProperties() {
        return getDelegate().getSystemProperties();
    }

    @Override
    default void setSystemProperties(Map<String, ? extends @Nullable Object> systemProperties) {
        getDelegate().setSystemProperties(systemProperties);
    }

    @Override
    default JavaForkOptions systemProperties(Map<String, ? extends @Nullable Object> properties) {
        getDelegate().systemProperties(properties);
        return this;
    }

    @Override
    default JavaForkOptions systemProperty(String name, @Nullable Object value) {
        getDelegate().systemProperty(name, value);
        return this;
    }

    @Override
    default Property<String> getDefaultCharacterEncoding() {
        return getDelegate().getDefaultCharacterEncoding();
    }

    @Override
    default void setDefaultCharacterEncoding(@Nullable String defaultCharacterEncoding) {
        getDelegate().setDefaultCharacterEncoding(defaultCharacterEncoding);
    }

    @Override
    default Property<String> getMinHeapSize() {
        return getDelegate().getMinHeapSize();
    }

    @Override
    default void setMinHeapSize(@Nullable String minHeapSize) {
        getDelegate().setMinHeapSize(minHeapSize);
    }

    @Override
    default Property<String> getMaxHeapSize() {
        return getDelegate().getMaxHeapSize();
    }

    @Override
    default void setMaxHeapSize(@Nullable String maxHeapSize) {
        getDelegate().setMaxHeapSize(maxHeapSize);
    }

    @Nullable
    @Override
    default ListProperty<String> getJvmArgs() {
        return getDelegate().getJvmArgs();
    }

    @Override
    default void setJvmArgs(List<String> arguments) {
        getDelegate().setJvmArgs(arguments);
    }

    @Override
    default void setJvmArgs(Iterable<?> arguments) {
        getDelegate().setJvmArgs(arguments);
    }

    @Override
    default JavaForkOptions jvmArgs(Iterable<?> arguments) {
        getDelegate().jvmArgs(arguments);
        return this;
    }

    @Override
    default JavaForkOptions jvmArgs(Object... arguments) {
        getDelegate().jvmArgs(arguments);
        return this;
    }

    @Override
    default ListProperty<CommandLineArgumentProvider> getJvmArgumentProviders() {
        return getDelegate().getJvmArgumentProviders();
    }

    @Override
    default ConfigurableFileCollection getBootstrapClasspath() {
        return getDelegate().getBootstrapClasspath();
    }

    @Override
    default void setBootstrapClasspath(FileCollection bootstrapClasspath) {
        getDelegate().setBootstrapClasspath(bootstrapClasspath);
    }

    @Override
    default JavaForkOptions bootstrapClasspath(Object... classpath) {
        getDelegate().bootstrapClasspath(classpath);
        return this;
    }

    @Override
    default Property<Boolean> getEnableAssertions() {
        return getDelegate().getEnableAssertions();
    }

    @Override
    default void setEnableAssertions(boolean enableAssertions) {
        getDelegate().setEnableAssertions(enableAssertions);
    }

    @Override
    default Property<Boolean> getDebug() {
        return getDelegate().getDebug();
    }

    @Override
    default void setDebug(boolean debug) {
        getDelegate().setDebug(debug);
    }

    @Override
    default JavaDebugOptions getDebugOptions() {
        return getDelegate().getDebugOptions();
    }

    @Override
    default void debugOptions(Action<JavaDebugOptions> action) {
        getDelegate().debugOptions(action);
    }

    @Override
    default Provider<List<String>> getAllJvmArgs() {
        return getDelegate().getAllJvmArgs();
    }

    @Override
    @SuppressWarnings("deprecation")
    default void setAllJvmArgs(List<String> arguments) {
        getDelegate().setAllJvmArgs(arguments);
    }

    @Override
    @SuppressWarnings("deprecation")
    default void setAllJvmArgs(Iterable<?> arguments) {
        getDelegate().setAllJvmArgs(arguments);
    }

    @Override
    default ListProperty<String> getJvmArguments() {
        return getDelegate().getJvmArguments();
    }

    @Override
    default JavaForkOptions copyTo(JavaForkOptions options) {
        getDelegate().copyTo(options);
        return this;
    }

    @Override
    JavaExecSpec getDelegate();
}
