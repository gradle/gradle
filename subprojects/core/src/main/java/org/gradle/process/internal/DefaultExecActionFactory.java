/*
 * Copyright 2014 the original author or authors.
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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.initialization.BuildCancellationToken;
import org.gradle.initialization.DefaultBuildCancellationToken;
import org.gradle.internal.concurrent.DefaultExecutorFactory;
import org.gradle.internal.concurrent.Stoppable;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.process.CommandLineArgumentProvider;
import org.gradle.process.JavaForkOptions;
import org.gradle.process.ProcessForkOptions;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

public class DefaultExecActionFactory implements ExecFactory, Stoppable {
    private final FileResolver fileResolver;
    private final DefaultExecutorFactory executorFactory = new DefaultExecutorFactory();
    private final Executor executor;
    private final BuildCancellationToken buildCancellationToken;

    public DefaultExecActionFactory(FileResolver fileResolver) {
        this(fileResolver, new DefaultBuildCancellationToken());
    }

    public DefaultExecActionFactory(FileResolver fileResolver, BuildCancellationToken buildCancellationToken) {
        this.fileResolver = fileResolver;
        this.buildCancellationToken = buildCancellationToken;
        executor = executorFactory.create("Exec process");
    }

    @Override
    public void stop() {
        executorFactory.stop();
    }

    @Override
    public ExecFactory forContext(FileResolver fileResolver, Instantiator instantiator) {
        return new DecoratingExecActionFactory(fileResolver, instantiator, executor, buildCancellationToken);
    }

    @Override
    public ExecAction newDecoratedExecAction() {
        throw new UnsupportedOperationException();
    }

    @Override
    public ExecAction newExecAction() {
        return new DefaultExecAction(fileResolver, executor, buildCancellationToken);
    }

    @Override
    public JavaForkOptionsInternal newJavaForkOptions() {
        return new DefaultJavaForkOptions(fileResolver);
    }

    @Override
    public JavaForkOptionsInternal immutableCopy(JavaForkOptionsInternal options) {
        DefaultJavaForkOptions copy = new DefaultJavaForkOptions(fileResolver);
        options.copyTo(copy);
        return new ImmutableJavaForkOptions(copy);
    }

    @Override
    public JavaExecAction newDecoratedJavaExecAction() {
        throw new UnsupportedOperationException();
    }

    @Override
    public JavaExecAction newJavaExecAction() {
        return new DefaultJavaExecAction(fileResolver, executor, buildCancellationToken);
    }

    @Override
    public ExecHandleBuilder newExec() {
        return new DefaultExecHandleBuilder(fileResolver, executor, buildCancellationToken);
    }

    @Override
    public JavaExecHandleBuilder newJavaExec() {
        return new JavaExecHandleBuilder(fileResolver, executor, buildCancellationToken);
    }

    private static class DecoratingExecActionFactory implements ExecFactory {
        private final FileResolver fileResolver;
        private final Instantiator instantiator;
        private final Executor executor;
        private final BuildCancellationToken buildCancellationToken;

        DecoratingExecActionFactory(FileResolver fileResolver, Instantiator instantiator, Executor executor, BuildCancellationToken buildCancellationToken) {
            this.fileResolver = fileResolver;
            this.instantiator = instantiator;
            this.executor = executor;
            this.buildCancellationToken = buildCancellationToken;
        }

        @Override
        public ExecFactory forContext(FileResolver fileResolver, Instantiator instantiator) {
            return new DecoratingExecActionFactory(fileResolver, instantiator, executor, buildCancellationToken);
        }

        @Override
        public ExecAction newExecAction() {
            return new DefaultExecAction(fileResolver, executor, buildCancellationToken);
        }

        @Override
        public JavaForkOptionsInternal newJavaForkOptions() {
            return new DefaultJavaForkOptions(fileResolver);
        }

        @Override
        public JavaForkOptionsInternal immutableCopy(JavaForkOptionsInternal options) {
            DefaultJavaForkOptions copy = new DefaultJavaForkOptions(fileResolver);
            options.copyTo(copy);
            return new ImmutableJavaForkOptions(copy);
        }

        @Override
        public JavaExecAction newJavaExecAction() {
            return new DefaultJavaExecAction(fileResolver, executor, buildCancellationToken);
        }

        @Override
        public ExecHandleBuilder newExec() {
            return new DefaultExecHandleBuilder(fileResolver, executor, buildCancellationToken);
        }

        @Override
        public JavaExecHandleBuilder newJavaExec() {
            return new JavaExecHandleBuilder(fileResolver, executor, buildCancellationToken);
        }

        @Override
        public ExecAction newDecoratedExecAction() {
            return instantiator.newInstance(DefaultExecAction.class, fileResolver, executor, buildCancellationToken);
        }

        @Override
        public JavaExecAction newDecoratedJavaExecAction() {
            return instantiator.newInstance(DefaultJavaExecAction.class, fileResolver, executor, buildCancellationToken);
        }
    }

    private static class ImmutableJavaForkOptions implements JavaForkOptionsInternal {
        private final JavaForkOptionsInternal delegate;

        public ImmutableJavaForkOptions(JavaForkOptionsInternal delegate) {
            this.delegate = delegate;
        }

        @Override
        public String getExecutable() {
            return delegate.getExecutable();
        }

        @Override
        public void setExecutable(String executable) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Map<String, Object> getSystemProperties() {
            return ImmutableMap.copyOf(delegate.getSystemProperties());
        }

        @Override
        public void setExecutable(Object executable) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void setSystemProperties(Map<String, ?> properties) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ProcessForkOptions executable(Object executable) {
            throw new UnsupportedOperationException();
        }

        @Override
        public JavaForkOptions systemProperties(Map<String, ?> properties) {
            throw new UnsupportedOperationException();
        }

        @Override
        public File getWorkingDir() {
            return delegate.getWorkingDir();
        }

        @Override
        public void setWorkingDir(File dir) {
            throw new UnsupportedOperationException();
        }

        @Override
        public JavaForkOptions systemProperty(String name, Object value) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void setWorkingDir(Object dir) {
            throw new UnsupportedOperationException();
        }

        @Override
        public String getDefaultCharacterEncoding() {
            return delegate.getDefaultCharacterEncoding();
        }

        @Override
        public ProcessForkOptions workingDir(Object dir) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Map<String, Object> getEnvironment() {
            return ImmutableMap.copyOf(delegate.getEnvironment());
        }

        @Override
        public void setEnvironment(Map<String, ?> environmentVariables) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void setDefaultCharacterEncoding(String defaultCharacterEncoding) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ProcessForkOptions environment(Map<String, ?> environmentVariables) {
            throw new UnsupportedOperationException();
        }

        @Override
        public String getMinHeapSize() {
            return delegate.getMinHeapSize();
        }

        @Override
        public void setMinHeapSize(String heapSize) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ProcessForkOptions environment(String name, Object value) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ProcessForkOptions copyTo(ProcessForkOptions options) {
            throw new UnsupportedOperationException();
        }

        @Override
        public String getMaxHeapSize() {
            return delegate.getMaxHeapSize();
        }

        @Override
        public void setMaxHeapSize(String heapSize) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<String> getJvmArgs() {
            return ImmutableList.copyOf(delegate.getJvmArgs());
        }

        @Override
        public void setJvmArgs(List<String> arguments) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void setJvmArgs(Iterable<?> arguments) {
            throw new UnsupportedOperationException();
        }

        @Override
        public JavaForkOptions jvmArgs(Iterable<?> arguments) {
            throw new UnsupportedOperationException();
        }

        @Override
        public JavaForkOptions jvmArgs(Object... arguments) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<CommandLineArgumentProvider> getJvmArgumentProviders() {
            throw new UnsupportedOperationException();
        }

        @Override
        public FileCollection getBootstrapClasspath() {
            return delegate.getBootstrapClasspath();
        }

        @Override
        public void setBootstrapClasspath(FileCollection classpath) {
            throw new UnsupportedOperationException();
        }

        @Override
        public JavaForkOptions bootstrapClasspath(Object... classpath) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean getEnableAssertions() {
            return delegate.getEnableAssertions();
        }

        @Override
        public void setEnableAssertions(boolean enabled) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean getDebug() {
            return delegate.getDebug();
        }

        @Override
        public void setDebug(boolean enabled) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<String> getAllJvmArgs() {
            return ImmutableList.copyOf(delegate.getAllJvmArgs());
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
        public JavaForkOptions copyTo(JavaForkOptions options) {
            return delegate.copyTo(options);
        }

        @Override
        public JavaForkOptionsInternal mergeWith(JavaForkOptions options) {
            return new ImmutableJavaForkOptions(delegate.mergeWith(options));
        }

        @Override
        public boolean isCompatibleWith(JavaForkOptions options) {
            return delegate.isCompatibleWith(options);
        }
    }
}
