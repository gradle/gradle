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
import org.gradle.api.file.FileCollection;
import org.gradle.api.jvm.ModularitySpec;
import org.gradle.api.provider.Property;
import org.gradle.process.CommandLineArgumentProvider;
import org.gradle.process.JavaDebugOptions;
import org.gradle.process.JavaExecSpec;
import org.gradle.process.JavaForkOptions;

import javax.annotation.Nullable;
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

    @SuppressWarnings("deprecation")
    @Nullable
    @Override
    default String getMain() {
        return getDelegate().getMain();
    }

    @SuppressWarnings("deprecation")
    @Override
    default JavaExecSpec setMain(@Nullable String main) {
        getDelegate().setMain(main);
        return this;
    }

    @Nullable
    @Override
    default List<String> getArgs() {
        return getDelegate().getArgs();
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
    default JavaExecSpec setArgs(@Nullable List<String> args) {
        getDelegate().setArgs(args);
        return this;
    }

    @Override
    default JavaExecSpec setArgs(@Nullable Iterable<?> args) {
        getDelegate().setArgs(args);
        return this;
    }

    @Override
    default List<CommandLineArgumentProvider> getArgumentProviders() {
        return getDelegate().getArgumentProviders();
    }

    @Override
    default JavaExecSpec classpath(Object... paths) {
        getDelegate().classpath(paths);
        return this;
    }

    @Override
    default FileCollection getClasspath() {
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
    default Map<String, Object> getSystemProperties() {
        return getDelegate().getSystemProperties();
    }

    @Override
    default void setSystemProperties(Map<String, ?> properties) {
        getDelegate().setSystemProperties(properties);
    }

    @Override
    default JavaForkOptions systemProperties(Map<String, ?> properties) {
        getDelegate().systemProperties(properties);
        return this;
    }

    @Override
    default JavaForkOptions systemProperty(String name, Object value) {
        getDelegate().systemProperty(name, value);
        return this;
    }

    @Nullable
    @Override
    default String getDefaultCharacterEncoding() {
        return getDelegate().getDefaultCharacterEncoding();
    }

    @Override
    default void setDefaultCharacterEncoding(@Nullable String defaultCharacterEncoding) {
        getDelegate().setDefaultCharacterEncoding(defaultCharacterEncoding);
    }

    @Nullable
    @Override
    default String getMinHeapSize() {
        return getDelegate().getMinHeapSize();
    }

    @Override
    default void setMinHeapSize(@Nullable String heapSize) {
        getDelegate().setMinHeapSize(heapSize);
    }

    @Nullable
    @Override
    default String getMaxHeapSize() {
        return getDelegate().getMaxHeapSize();
    }

    @Override
    default void setMaxHeapSize(@Nullable String heapSize) {
        getDelegate().setMaxHeapSize(heapSize);
    }

    @Nullable
    @Override
    default List<String> getJvmArgs() {
        return getDelegate().getJvmArgs();
    }

    @Override
    default void setJvmArgs(@Nullable List<String> arguments) {
        getDelegate().setJvmArgs(arguments);
    }

    @Override
    default void setJvmArgs(@Nullable Iterable<?> arguments) {
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
    default List<CommandLineArgumentProvider> getJvmArgumentProviders() {
        return getDelegate().getJvmArgumentProviders();
    }

    @Override
    default FileCollection getBootstrapClasspath() {
        return getDelegate().getBootstrapClasspath();
    }

    @Override
    default void setBootstrapClasspath(FileCollection classpath) {
        getDelegate().setBootstrapClasspath(classpath);
    }

    @Override
    default JavaForkOptions bootstrapClasspath(Object... classpath) {
        getDelegate().bootstrapClasspath(classpath);
        return this;
    }

    @Override
    default boolean getEnableAssertions() {
        return getDelegate().getEnableAssertions();
    }

    @Override
    default void setEnableAssertions(boolean enabled) {
        getDelegate().setEnableAssertions(enabled);
    }

    @Override
    default boolean getDebug() {
        return getDelegate().getDebug();
    }

    @Override
    default void setDebug(boolean enabled) {
        getDelegate().setDebug(enabled);
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
    default List<String> getAllJvmArgs() {
        return getDelegate().getAllJvmArgs();
    }

    @Override
    default void setAllJvmArgs(List<String> arguments) {
        getDelegate().setAllJvmArgs(arguments);
    }

    @Override
    default void setAllJvmArgs(Iterable<?> arguments) {
        getDelegate().setAllJvmArgs(arguments);
    }

    @Override
    default JavaForkOptions copyTo(JavaForkOptions options) {
        getDelegate().copyTo(options);
        return this;
    }

    @Override
    JavaExecSpec getDelegate();
}
