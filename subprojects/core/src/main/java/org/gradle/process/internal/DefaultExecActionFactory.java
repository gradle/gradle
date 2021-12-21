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

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.gradle.api.Action;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.ExternalProcessStartedListener;
import org.gradle.api.internal.file.DefaultFileCollectionFactory;
import org.gradle.api.internal.file.DefaultFileLookup;
import org.gradle.api.internal.file.FileCollectionFactory;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.internal.file.collections.DefaultDirectoryFileTreeFactory;
import org.gradle.api.internal.file.temp.GradleUserHomeTemporaryFileProvider;
import org.gradle.api.internal.file.temp.TemporaryFileProvider;
import org.gradle.api.internal.model.InstantiatorBackedObjectFactory;
import org.gradle.api.internal.provider.PropertyHost;
import org.gradle.api.internal.tasks.DefaultTaskDependencyFactory;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.tasks.util.PatternSet;
import org.gradle.api.tasks.util.internal.PatternSets;
import org.gradle.initialization.BuildCancellationToken;
import org.gradle.initialization.DefaultBuildCancellationToken;
import org.gradle.initialization.GradleUserHomeDirProvider;
import org.gradle.internal.Factory;
import org.gradle.internal.concurrent.CompositeStoppable;
import org.gradle.internal.concurrent.DefaultExecutorFactory;
import org.gradle.internal.concurrent.ExecutorFactory;
import org.gradle.internal.concurrent.Stoppable;
import org.gradle.internal.jvm.JavaModuleDetector;
import org.gradle.internal.jvm.Jvm;
import org.gradle.internal.nativeintegration.services.FileSystems;
import org.gradle.internal.reflect.DirectInstantiator;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.process.CommandLineArgumentProvider;
import org.gradle.process.ExecResult;
import org.gradle.process.ExecSpec;
import org.gradle.process.JavaDebugOptions;
import org.gradle.process.JavaExecSpec;
import org.gradle.process.JavaForkOptions;
import org.gradle.process.ProcessForkOptions;

import javax.annotation.Nullable;
import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

import static java.util.Objects.requireNonNull;

public abstract class DefaultExecActionFactory implements ExecFactory {
    protected final FileResolver fileResolver;
    protected final Executor executor;
    protected final FileCollectionFactory fileCollectionFactory;
    protected final ObjectFactory objectFactory;
    protected final TemporaryFileProvider temporaryFileProvider;
    @Nullable
    protected final JavaModuleDetector javaModuleDetector;
    protected final BuildCancellationToken buildCancellationToken;

    private DefaultExecActionFactory(
        FileResolver fileResolver,
        FileCollectionFactory fileCollectionFactory,
        ObjectFactory objectFactory,
        Executor executor,
        TemporaryFileProvider temporaryFileProvider,
        @Nullable JavaModuleDetector javaModuleDetector,
        BuildCancellationToken buildCancellationToken
    ) {
        this.fileResolver = fileResolver;
        this.fileCollectionFactory = fileCollectionFactory;
        this.objectFactory = objectFactory;
        this.temporaryFileProvider = temporaryFileProvider;
        this.javaModuleDetector = javaModuleDetector;
        this.buildCancellationToken = buildCancellationToken;
        this.executor = executor;
    }

    // Do not use this. It's here because some of the services this type needs are not easily accessed in certain cases and will be removed ay some point. Use one of the other methods instead
    @Deprecated
    public static DefaultExecActionFactory root(File gradleUserHome) {
        requireNonNull(gradleUserHome, "gradleUserHome");
        Factory<PatternSet> patternSetFactory = PatternSets.getNonCachingPatternSetFactory();
        FileResolver resolver = new DefaultFileLookup().getFileResolver();
        DefaultFileCollectionFactory fileCollectionFactory = new DefaultFileCollectionFactory(resolver, DefaultTaskDependencyFactory.withNoAssociatedProject(), new DefaultDirectoryFileTreeFactory(), patternSetFactory, PropertyHost.NO_OP, FileSystems.getDefault());
        GradleUserHomeDirProvider userHomeDirProvider = () -> gradleUserHome;
        TemporaryFileProvider temporaryFileProvider = new GradleUserHomeTemporaryFileProvider(userHomeDirProvider);
        return of(resolver, fileCollectionFactory, new InstantiatorBackedObjectFactory(DirectInstantiator.INSTANCE), new DefaultExecutorFactory(), new DefaultBuildCancellationToken(), temporaryFileProvider);
    }

    public static DefaultExecActionFactory of(
        FileResolver fileResolver,
        FileCollectionFactory fileCollectionFactory,
        ExecutorFactory executorFactory,
        TemporaryFileProvider temporaryFileProvider
    ) {
        return of(fileResolver, fileCollectionFactory, new InstantiatorBackedObjectFactory(DirectInstantiator.INSTANCE), executorFactory, new DefaultBuildCancellationToken(), temporaryFileProvider);
    }

    private static DefaultExecActionFactory of(
        FileResolver fileResolver,
        FileCollectionFactory fileCollectionFactory,
        ObjectFactory objectFactory,
        ExecutorFactory executorFactory,
        BuildCancellationToken buildCancellationToken,
        TemporaryFileProvider temporaryFileProvider
    ) {
        return new RootExecFactory(fileResolver, fileCollectionFactory, objectFactory, executorFactory, buildCancellationToken, temporaryFileProvider);
    }

    @Override
    public Builder forContext() {
        return new BuilderImpl(executor, temporaryFileProvider)
            .withFileResolver(fileResolver)
            .withFileCollectionFactory(fileCollectionFactory)
            .withBuildCancellationToken(buildCancellationToken)
            .withObjectFactory(objectFactory)
            .withJavaModuleDetector(javaModuleDetector);
    }

    public ExecAction newDecoratedExecAction() {
        throw new UnsupportedOperationException();
    }

    @Override
    public ExecAction newExecAction() {
        return new DefaultExecAction(fileResolver, executor, buildCancellationToken);
    }

    @Override
    public JavaForkOptionsInternal newDecoratedJavaForkOptions() {
        throw new UnsupportedOperationException();
    }

    @Override
    public JavaForkOptionsInternal newJavaForkOptions() {
        final DefaultJavaForkOptions forkOptions = new DefaultJavaForkOptions(fileResolver, fileCollectionFactory, new DefaultJavaDebugOptions());
        if (forkOptions.getExecutable() == null) {
            forkOptions.setExecutable(Jvm.current().getJavaExecutable());
        }
        return forkOptions;
    }

    @Override
    public JavaForkOptionsInternal immutableCopy(JavaForkOptionsInternal options) {
        @SuppressWarnings("deprecation")
        Factory<PatternSet> nonCachingPatternSetFactory = PatternSets.getNonCachingPatternSetFactory();
        // NOTE: We do not want/need a decorated version of JavaForkOptions or JavaDebugOptions because
        // these immutable instances are held across builds and will retain classloaders/services in the decorated object
        DefaultFileCollectionFactory fileCollectionFactory = new DefaultFileCollectionFactory(fileResolver, DefaultTaskDependencyFactory.withNoAssociatedProject(), new DefaultDirectoryFileTreeFactory(), nonCachingPatternSetFactory, PropertyHost.NO_OP, FileSystems.getDefault());
        JavaForkOptionsInternal copy = new DefaultJavaForkOptions(fileResolver, fileCollectionFactory, new DefaultJavaDebugOptions());
        options.copyTo(copy);
        return new ImmutableJavaForkOptions(copy);
    }

    public JavaExecAction newDecoratedJavaExecAction() {
        throw new UnsupportedOperationException();
    }

    @Override
    public JavaExecAction newJavaExecAction() {
        return new DefaultJavaExecAction(fileResolver, fileCollectionFactory, objectFactory, executor, buildCancellationToken, temporaryFileProvider, javaModuleDetector, newJavaForkOptions());
    }

    @Override
    public ExecHandleBuilder newExec() {
        return new DefaultExecHandleBuilder(fileResolver, executor, buildCancellationToken);
    }

    @Override
    public JavaExecHandleBuilder newJavaExec() {
        return new JavaExecHandleBuilder(fileResolver, fileCollectionFactory, objectFactory, executor, buildCancellationToken, temporaryFileProvider, javaModuleDetector, newJavaForkOptions());
    }

    @Override
    public ExecResult javaexec(Action<? super JavaExecSpec> action) {
        JavaExecAction execAction = newDecoratedJavaExecAction();
        action.execute(execAction);
        return execAction.execute();
    }

    @Override
    public ExecResult exec(Action<? super ExecSpec> action) {
        ExecAction execAction = newDecoratedExecAction();
        action.execute(execAction);
        return execAction.execute();
    }

    private static class BuilderImpl implements Builder {
        // The executor is always inherited from the parent
        private final Executor executor;
        // The temporaryFileProvider is always inherited from the parent
        private final TemporaryFileProvider temporaryFileProvider;

        private FileResolver fileResolver;
        private FileCollectionFactory fileCollectionFactory;
        private Instantiator instantiator;
        private BuildCancellationToken buildCancellationToken;
        private ObjectFactory objectFactory;
        @Nullable
        private JavaModuleDetector javaModuleDetector;

        @Nullable
        private ExternalProcessStartedListener externalProcessStartedListener;

        BuilderImpl(
            Executor executor,
            TemporaryFileProvider temporaryFileProvider
        ) {
            this.executor = executor;
            this.temporaryFileProvider = temporaryFileProvider;
        }

        @Override
        public Builder withFileResolver(FileResolver fileResolver) {
            this.fileResolver = fileResolver;
            return this;
        }

        @Override
        public Builder withFileCollectionFactory(FileCollectionFactory fileCollectionFactory) {
            this.fileCollectionFactory = fileCollectionFactory;
            return this;
        }

        @Override
        public Builder withInstantiator(Instantiator instantiator) {
            this.instantiator = instantiator;
            return this;
        }

        @Override
        public Builder withObjectFactory(ObjectFactory objectFactory) {
            this.objectFactory = objectFactory;
            return this;
        }

        @Override
        public Builder withJavaModuleDetector(@Nullable JavaModuleDetector javaModuleDetector) {
            this.javaModuleDetector = javaModuleDetector;
            return this;
        }

        @Override
        public Builder withBuildCancellationToken(BuildCancellationToken buildCancellationToken) {
            this.buildCancellationToken = buildCancellationToken;
            return this;
        }

        @Override
        public Builder withExternalProcessStartedListener(@Nullable ExternalProcessStartedListener externalProcessStartedListener) {
            this.externalProcessStartedListener = externalProcessStartedListener;
            return this;
        }

        @Override
        public Builder withoutExternalProcessStartedListener() {
            this.externalProcessStartedListener = null;
            return this;
        }

        @Override
        public ExecFactory build() {
            Preconditions.checkState(fileResolver != null, "fileResolver is not set");
            Preconditions.checkState(fileCollectionFactory != null, "fileCollectionFactory is not set");
            Preconditions.checkState(instantiator != null, "instantiator is not set");
            Preconditions.checkState(buildCancellationToken != null, "buildCancellationToken is not set");
            Preconditions.checkState(objectFactory != null, "objectFactory is not set");
            return new DecoratingExecActionFactory(
                fileResolver,
                fileCollectionFactory,
                instantiator,
                executor,
                temporaryFileProvider,
                buildCancellationToken,
                objectFactory,
                javaModuleDetector,
                externalProcessStartedListener);
        }
    }

    private static class RootExecFactory extends DefaultExecActionFactory implements Stoppable {
        public RootExecFactory(
            FileResolver fileResolver,
            FileCollectionFactory fileCollectionFactory,
            ObjectFactory objectFactory,
            ExecutorFactory executorFactory,
            BuildCancellationToken buildCancellationToken,
            TemporaryFileProvider temporaryFileProvider
        ) {
            super(fileResolver, fileCollectionFactory, objectFactory, executorFactory.create("Exec process"), temporaryFileProvider, null, buildCancellationToken);
        }

        @Override
        public void stop() {
            CompositeStoppable.stoppable(executor).stop();
        }
    }

    private static class DecoratingExecActionFactory extends DefaultExecActionFactory {
        private final Instantiator instantiator;
        @Nullable
        private final ExternalProcessStartedListener externalProcessStartedListener;

        DecoratingExecActionFactory(
            FileResolver fileResolver,
            FileCollectionFactory fileCollectionFactory,
            Instantiator instantiator,
            Executor executor,
            TemporaryFileProvider temporaryFileProvider,
            BuildCancellationToken buildCancellationToken,
            ObjectFactory objectFactory,
            @Nullable JavaModuleDetector javaModuleDetector,
            @Nullable ExternalProcessStartedListener externalProcessStartedListener
        ) {
            super(fileResolver, fileCollectionFactory, objectFactory, executor, temporaryFileProvider, javaModuleDetector, buildCancellationToken);
            this.instantiator = instantiator;
            this.externalProcessStartedListener = externalProcessStartedListener;
        }

        @Override
        public ExecAction newDecoratedExecAction() {
            DefaultExecAction execAction = instantiator.newInstance(DefaultExecAction.class, fileResolver, executor, buildCancellationToken);
            ExecHandleListener listener = getExecHandleListener();
            if (listener != null) {
                execAction.listener(listener);
            }
            return execAction;
        }

        @Override
        public JavaExecAction newDecoratedJavaExecAction() {
            final JavaForkOptionsInternal forkOptions = newDecoratedJavaForkOptions();
            forkOptions.setExecutable(Jvm.current().getJavaExecutable());
            DefaultJavaExecAction javaExecAction = instantiator.newInstance(
                DefaultJavaExecAction.class,
                fileResolver,
                fileCollectionFactory,
                objectFactory,
                executor,
                buildCancellationToken,
                temporaryFileProvider,
                javaModuleDetector,
                forkOptions
            );
            ExecHandleListener listener = getExecHandleListener();
            if (listener != null) {
                javaExecAction.listener(listener);
            }
            return javaExecAction;
        }

        @Override
        public JavaForkOptionsInternal newDecoratedJavaForkOptions() {
            JavaDebugOptions javaDebugOptions = objectFactory.newInstance(DefaultJavaDebugOptions.class, objectFactory);
            final DefaultJavaForkOptions forkOptions = instantiator.newInstance(DefaultJavaForkOptions.class, fileResolver, fileCollectionFactory, javaDebugOptions);
            forkOptions.setExecutable(Jvm.current().getJavaExecutable());
            return forkOptions;
        }

        @Override
        public Builder forContext() {
            return super.forContext().withInstantiator(instantiator).withExternalProcessStartedListener(externalProcessStartedListener);
        }

        @Nullable
        private ExecHandleListener getExecHandleListener() {
            if (externalProcessStartedListener == null) {
                return null;
            }

            return new ExecHandleListener() {
                @Override
                public void beforeExecutionStarted(ExecHandle execHandle) {
                    StringBuilder command = new StringBuilder(execHandle.getCommand());
                    for (String argument : execHandle.getArguments()) {
                        command.append(' ').append(argument);
                    }
                    externalProcessStartedListener.onExternalProcessStarted(command.toString(), /* consumer */ null);
                }

                @Override
                public void executionStarted(ExecHandle execHandle) {
                }

                @Override
                public void executionFinished(ExecHandle execHandle, ExecResult execResult) {
                }
            };
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
        public JavaDebugOptions getDebugOptions() {
            return delegate.getDebugOptions();
        }

        @Override
        public void debugOptions(Action<JavaDebugOptions> action) {
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
        public boolean isCompatibleWith(JavaForkOptions options) {
            return delegate.isCompatibleWith(options);
        }
    }
}
