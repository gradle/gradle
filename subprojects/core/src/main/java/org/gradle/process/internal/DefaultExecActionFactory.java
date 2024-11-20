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
import org.gradle.api.Action;
import org.gradle.api.internal.ExternalProcessStartedListener;
import org.gradle.api.internal.file.DefaultFileCollectionFactory;
import org.gradle.api.internal.file.FileCollectionFactory;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.internal.file.collections.DefaultDirectoryFileTreeFactory;
import org.gradle.api.internal.file.temp.TemporaryFileProvider;
import org.gradle.api.internal.provider.PropertyHost;
import org.gradle.api.internal.tasks.DefaultTaskDependencyFactory;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.tasks.util.PatternSet;
import org.gradle.api.tasks.util.internal.PatternSets;
import org.gradle.initialization.BuildCancellationToken;
import org.gradle.internal.Factory;
import org.gradle.internal.concurrent.ExecutorFactory;
import org.gradle.internal.jvm.JavaModuleDetector;
import org.gradle.internal.jvm.Jvm;
import org.gradle.internal.nativeintegration.services.FileSystems;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.process.ExecResult;
import org.gradle.process.ExecSpec;
import org.gradle.process.JavaExecSpec;

import javax.annotation.Nullable;
import java.util.concurrent.Executor;

public class DefaultExecActionFactory implements ExecFactory {
    protected final FileResolver fileResolver;
    protected final Executor executor;
    protected final FileCollectionFactory fileCollectionFactory;
    protected final ObjectFactory objectFactory;
    protected final TemporaryFileProvider temporaryFileProvider;
    @Nullable
    protected final JavaModuleDetector javaModuleDetector;
    protected final BuildCancellationToken buildCancellationToken;
    protected final ClientExecHandleBuilderFactory execHandleFactory;
    protected final Instantiator instantiator;
    protected final ExternalProcessStartedListener externalProcessStartedListener;

    private DefaultExecActionFactory(
        FileResolver fileResolver,
        FileCollectionFactory fileCollectionFactory,
        Instantiator instantiator,
        Executor executor,
        TemporaryFileProvider temporaryFileProvider,
        BuildCancellationToken buildCancellationToken,
        ObjectFactory objectFactory,
        ClientExecHandleBuilderFactory execHandleFactory,
        @Nullable JavaModuleDetector javaModuleDetector,
        @Nullable ExternalProcessStartedListener externalProcessStartedListener
    ) {
        this.fileResolver = fileResolver;
        this.fileCollectionFactory = fileCollectionFactory;
        this.objectFactory = objectFactory;
        this.temporaryFileProvider = temporaryFileProvider;
        this.javaModuleDetector = javaModuleDetector;
        this.buildCancellationToken = buildCancellationToken;
        this.executor = executor;
        this.execHandleFactory = execHandleFactory;
        this.instantiator = instantiator;
        this.externalProcessStartedListener = externalProcessStartedListener;
    }

    public static DefaultExecActionFactory of(
        FileResolver fileResolver,
        FileCollectionFactory fileCollectionFactory,
        Instantiator instantiator,
        ExecutorFactory executorFactory,
        TemporaryFileProvider temporaryFileProvider,
        BuildCancellationToken buildCancellationToken,
        ObjectFactory objectFactory,
        ClientExecHandleBuilderFactory clientExecHandleBuilderFactory
    ) {
        return new DefaultExecActionFactory(
            fileResolver,
            fileCollectionFactory,
            instantiator,
            executorFactory.create("Exec process"),
            temporaryFileProvider,
            buildCancellationToken,
            objectFactory,
            clientExecHandleBuilderFactory,
            null,
            null
        );
    }

    public ExecAction newDecoratedExecAction() {
        DefaultExecAction execAction = instantiator.newInstance(DefaultExecAction.class, execHandleFactory.newExecHandleBuilder());
        ExecHandleListener listener = getExecHandleListener();
        if (listener != null) {
            execAction.listener(listener);
        }
        return execAction;
    }

    @Override
    public ExecAction newExecAction() {
        return new DefaultExecAction(execHandleFactory.newExecHandleBuilder());
    }

    @Override
    public JavaForkOptionsInternal newDecoratedJavaForkOptions() {
        final DefaultJavaForkOptions forkOptions = instantiator.newInstance(DefaultJavaForkOptions.class, objectFactory, fileResolver, fileCollectionFactory);
        forkOptions.setExecutable(Jvm.current().getJavaExecutable());
        return forkOptions;
    }

    @Override
    public JavaForkOptionsInternal newJavaForkOptions() {
        final DefaultJavaForkOptions forkOptions = objectFactory.newInstance(DefaultJavaForkOptions.class, objectFactory, fileResolver, fileCollectionFactory);
        if (forkOptions.getExecutable() == null) {
            forkOptions.setExecutable(Jvm.current().getJavaExecutable());
        }
        return forkOptions;
    }

    @Override
    public EffectiveJavaForkOptions toEffectiveJavaForkOptions(JavaForkOptionsInternal options) {
        @SuppressWarnings("deprecation")
        Factory<PatternSet> nonCachingPatternSetFactory = PatternSets.getNonCachingPatternSetFactory();
        // NOTE: We do not want/need a decorated version of JvmOptions or JavaDebugOptions because
        // these immutable instances are held across builds and will retain classloaders/services in the decorated object
        DefaultFileCollectionFactory fileCollectionFactory = new DefaultFileCollectionFactory(fileResolver, DefaultTaskDependencyFactory.withNoAssociatedProject(), new DefaultDirectoryFileTreeFactory(), nonCachingPatternSetFactory, PropertyHost.NO_OP, FileSystems.getDefault());
        return options.toEffectiveJavaForkOptions(fileCollectionFactory);
    }

    public JavaExecAction newDecoratedJavaExecAction() {
        final JavaForkOptionsInternal forkOptions = newDecoratedJavaForkOptions();
        forkOptions.setExecutable(Jvm.current().getJavaExecutable());
        DefaultJavaExecAction javaExecAction = instantiator.newInstance(DefaultJavaExecAction.class, newJavaExec());
        ExecHandleListener listener = getExecHandleListener();
        if (listener != null) {
            javaExecAction.listener(listener);
        }
        return javaExecAction;
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

    @Override
    public JavaExecAction newJavaExecAction() {
        return new DefaultJavaExecAction(newJavaExec());
    }

    @Override
    @SuppressWarnings("deprecation")
    public ExecHandleBuilder newExec() {
        return new DefaultExecHandleBuilder(execHandleFactory.newExecHandleBuilder());
    }

    @Override
    public JavaExecHandleBuilder newJavaExec() {
        return new JavaExecHandleBuilder(
            fileCollectionFactory,
            objectFactory,
            temporaryFileProvider,
            javaModuleDetector,
            newJavaForkOptions(),
            execHandleFactory.newExecHandleBuilder()
        );
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

    @Override
    public Builder forContext() {
        return new BuilderImpl(executor, temporaryFileProvider)
            .withInstantiator(instantiator)
            .withExternalProcessStartedListener(externalProcessStartedListener)
            .withFileResolver(fileResolver)
            .withFileCollectionFactory(fileCollectionFactory)
            .withBuildCancellationToken(buildCancellationToken)
            .withObjectFactory(objectFactory)
            .withJavaModuleDetector(javaModuleDetector)
            .withExecHandleFactory(execHandleFactory);
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
        private ClientExecHandleBuilderFactory clientExecHandleBuilderFactory;
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
        public Builder withExecHandleFactory(ClientExecHandleBuilderFactory execHandleBuilderFactory) {
            this.clientExecHandleBuilderFactory = execHandleBuilderFactory;
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
            Preconditions.checkState(clientExecHandleBuilderFactory != null, "clientExecHandleFactory is not set");
            return new DefaultExecActionFactory(
                fileResolver,
                fileCollectionFactory,
                instantiator,
                executor,
                temporaryFileProvider,
                buildCancellationToken,
                objectFactory,
                clientExecHandleBuilderFactory,
                javaModuleDetector,
                externalProcessStartedListener
            );
        }
    }
}
