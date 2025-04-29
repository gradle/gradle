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

package org.gradle.process.internal.worker.child;

import org.gradle.api.Action;
import org.gradle.initialization.GradleUserHomeDirProvider;
import org.gradle.internal.concurrent.CompositeStoppable;
import org.gradle.internal.concurrent.DefaultExecutorFactory;
import org.gradle.internal.concurrent.ExecutorFactory;
import org.gradle.internal.event.DefaultListenerManager;
import org.gradle.internal.event.ListenerManager;
import org.gradle.internal.event.ScopedListenerManager;
import org.gradle.internal.logging.LoggingManagerFactory;
import org.gradle.internal.logging.LoggingManagerInternal;
import org.gradle.internal.logging.services.LoggingServiceRegistry;
import org.gradle.internal.nativeintegration.services.NativeServices;
import org.gradle.internal.nativeintegration.services.NativeServices.NativeServicesMode;
import org.gradle.internal.remote.MessagingClient;
import org.gradle.internal.remote.ObjectConnection;
import org.gradle.internal.remote.services.MessagingServices;
import org.gradle.internal.serialize.InputStreamBackedDecoder;
import org.gradle.internal.service.CloseableServiceRegistry;
import org.gradle.internal.service.Provides;
import org.gradle.internal.service.ServiceRegistration;
import org.gradle.internal.service.ServiceRegistrationProvider;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.internal.service.ServiceRegistryBuilder;
import org.gradle.internal.service.scopes.Scope.Global;
import org.gradle.process.internal.health.memory.DefaultJvmMemoryInfo;
import org.gradle.process.internal.health.memory.DefaultMemoryManager;
import org.gradle.process.internal.health.memory.DisabledOsMemoryInfo;
import org.gradle.process.internal.health.memory.JvmMemoryInfo;
import org.gradle.process.internal.health.memory.JvmMemoryStatus;
import org.gradle.process.internal.health.memory.JvmMemoryStatusListener;
import org.gradle.process.internal.health.memory.MemoryManager;
import org.gradle.process.internal.health.memory.OsMemoryInfo;
import org.gradle.process.internal.worker.WorkerJvmMemoryInfoSerializer;
import org.gradle.process.internal.worker.WorkerLoggingSerializer;
import org.gradle.process.internal.worker.WorkerProcessContext;
import org.gradle.process.internal.worker.messaging.WorkerConfig;
import org.gradle.process.internal.worker.messaging.WorkerConfigSerializer;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.Callable;

/**
 * <p>Stage 2 of the start-up for a worker process with the application classes loaded in the system ClassLoader. Takes
 * care of deserializing and invoking the worker action.</p>
 *
 * <p> Instantiated in the implementation ClassLoader and invoked from {@link org.gradle.process.internal.worker.GradleWorkerMain}.
 * See {@link ApplicationClassesInSystemClassLoaderWorkerImplementationFactory} for details.</p>
 */
public class SystemApplicationClassLoaderWorker implements Callable<Void> {
    private final DataInputStream configInputStream;

    public SystemApplicationClassLoaderWorker(DataInputStream configInputStream) {
        this.configInputStream = configInputStream;
    }

    @Override
    public Void call() throws Exception {
        if (System.getProperty("org.gradle.worker.test.stuck") != null) {
            // Simulate a stuck worker. There's probably a way to inject this failure...
            Thread.sleep(30000);
            return null;
        }

        WorkerConfig config = new WorkerConfigSerializer().read(new InputStreamBackedDecoder(configInputStream));

        // Read logging config and setup logging
        ServiceRegistry loggingServiceRegistry = LoggingServiceRegistry.newEmbeddableLogging();
        LoggingManagerInternal loggingManager = createLoggingManager(loggingServiceRegistry).setLevelInternal(config.getLogLevel());

        // When not explicitly set, use the value from system properties
        NativeServicesMode nativeServicesMode = config.getNativeServicesMode() == NativeServicesMode.NOT_SET
            ? NativeServicesMode.fromSystemProperties()
            : config.getNativeServicesMode();

        // Configure services
        File gradleUserHomeDir = new File(config.getGradleUserHomeDirPath());
        NativeServices.initializeOnWorker(gradleUserHomeDir, nativeServicesMode);
        ServiceRegistry basicWorkerServices = createBasicWorkerServices(NativeServices.getInstance(), loggingServiceRegistry);
        ServiceRegistry workerServices = WorkerServices.create(basicWorkerServices, gradleUserHomeDir);
        WorkerLogEventListener workerLogEventListener = workerServices.get(WorkerLogEventListener.class);

        File workingDirectory = workerServices.get(WorkerDirectoryProvider.class).getWorkingDirectory();
        File errorLog = getLastResortErrorLogFile(workingDirectory);
        PrintUnrecoverableErrorToFileHandler unrecoverableErrorHandler = new PrintUnrecoverableErrorToFileHandler(errorLog);

        ObjectConnection connection = null;
        try {
            // Read server address and start connecting
            connection = basicWorkerServices.get(MessagingClient.class).getConnection(config.getServerAddress());
            connection.addUnrecoverableErrorHandler(unrecoverableErrorHandler);
            configureLogging(loggingManager, connection, workerLogEventListener);
            // start logging now that the logging manager is connected
            loggingManager.start();
            if (config.shouldPublishJvmMemoryInfo()) {
                configureWorkerJvmMemoryInfoEvents(workerServices, connection);
            }

            ActionExecutionWorker worker = new ActionExecutionWorker(config.getWorkerAction());
            worker.execute(new ContextImpl(config.getWorkerId(), config.getDisplayName(), connection, workerServices));
        } finally {
            try {
                loggingManager.removeOutputEventListener(workerLogEventListener);
                CompositeStoppable.stoppable(connection, basicWorkerServices).stop();
                loggingManager.stop();
            } catch (Throwable t) {
                // We're failing while shutting down, so log whatever might have happened.
                unrecoverableErrorHandler.execute(t);
            }
        }

        return null;
    }

    private File getLastResortErrorLogFile(File workingDirectory) {
        return new File(workingDirectory, "worker-error-" + new SimpleDateFormat("yyyyMMddHHmmss").format(new Date()) + ".txt");
    }

    private static class PrintUnrecoverableErrorToFileHandler implements Action<Throwable> {
        private final File errorLog;

        private PrintUnrecoverableErrorToFileHandler(File errorLog) {
            this.errorLog = errorLog;
        }

        @Override
        public void execute(Throwable throwable) {
            try {
                final PrintStream ps = new PrintStream(errorLog);
                try {
                    ps.println("Encountered unrecoverable error:");
                    throwable.printStackTrace(ps);
                } finally {
                    ps.close();
                }
            } catch (FileNotFoundException e) {
                // ignore this, we won't be able to get any logs
            }
        }
    }

    private void configureLogging(LoggingManagerInternal loggingManager, ObjectConnection connection, WorkerLogEventListener workerLogEventListener) {
        connection.useParameterSerializers(WorkerLoggingSerializer.create());
        WorkerLoggingProtocol workerLoggingProtocol = connection.addOutgoing(WorkerLoggingProtocol.class);
        workerLogEventListener.setWorkerLoggingProtocol(workerLoggingProtocol);
        loggingManager.addOutputEventListener(workerLogEventListener);
    }

    private void configureWorkerJvmMemoryInfoEvents(ServiceRegistry workerServices, ObjectConnection connection) {
        connection.useParameterSerializers(WorkerJvmMemoryInfoSerializer.create());
        final WorkerJvmMemoryInfoProtocol workerJvmMemoryInfoProtocol = connection.addOutgoing(WorkerJvmMemoryInfoProtocol.class);
        workerServices.get(MemoryManager.class).addListener(new JvmMemoryStatusListener() {
            @Override
            public void onJvmMemoryStatus(JvmMemoryStatus jvmMemoryStatus) {
                workerJvmMemoryInfoProtocol.sendJvmMemoryStatus(jvmMemoryStatus);
            }
        });
    }

    LoggingManagerInternal createLoggingManager(ServiceRegistry loggingServiceRegistry) {
        LoggingManagerInternal loggingManagerInternal = loggingServiceRegistry.get(LoggingManagerFactory.class).createLoggingManager();
        loggingManagerInternal.captureSystemSources();
        return loggingManagerInternal;
    }

    private static ServiceRegistry createBasicWorkerServices(ServiceRegistry nativeServices, ServiceRegistry loggingServiceRegistry) {
        return ServiceRegistryBuilder.builder()
            .displayName("basic worker services")
            .parent(nativeServices)
            .parent(loggingServiceRegistry)
            .provider(new ServiceRegistrationProvider() {
                @SuppressWarnings("unused")
                void configure(ServiceRegistration registration) {
                    registration.add(ExecutorFactory.class, new DefaultExecutorFactory());
                }
            })
            .provider(new MessagingServices())
            .build();
    }

    private static class WorkerServices implements ServiceRegistrationProvider {

        public static CloseableServiceRegistry create(ServiceRegistry parent, File gradleUserHomeDir) {
            return ServiceRegistryBuilder.builder()
                .displayName("worker services")
                .parent(parent)
                .provider(new WorkerServices(gradleUserHomeDir))
                .build();
        }

        private final File gradleUserHomeDir;

        public WorkerServices(File gradleUserHomeDir) {
            this.gradleUserHomeDir = gradleUserHomeDir;
        }

        @Provides
        GradleUserHomeDirProvider createGradleUserHomeDirProvider() {
            return new GradleUserHomeDirProvider() {
                @Override
                public File getGradleUserHomeDirectory() {
                    return gradleUserHomeDir;
                }
            };
        }

        @Provides
        ScopedListenerManager createListenerManager() {
            return new DefaultListenerManager(Global.class);
        }

        @Provides
        OsMemoryInfo createOsMemoryInfo() {
            return new DisabledOsMemoryInfo();
        }

        @Provides
        JvmMemoryInfo createJvmMemoryInfo() {
            return new DefaultJvmMemoryInfo();
        }

        @Provides
        MemoryManager createMemoryManager(OsMemoryInfo osMemoryInfo, JvmMemoryInfo jvmMemoryInfo, ListenerManager listenerManager, ExecutorFactory executorFactory) {
            return new DefaultMemoryManager(osMemoryInfo, jvmMemoryInfo, listenerManager, executorFactory);
        }

        @Provides
        WorkerDirectoryProvider createWorkerDirectoryProvider(GradleUserHomeDirProvider gradleUserHomeDirProvider) {
            return new DefaultWorkerDirectoryProvider(gradleUserHomeDirProvider);
        }

        @Provides
        WorkerLogEventListener createWorkerLogEventListener() {
            return new WorkerLogEventListener();
        }
    }

    private static class ContextImpl implements WorkerProcessContext {
        private final long workerId;
        private final String displayName;
        private final ObjectConnection serverConnection;
        private final ServiceRegistry workerServices;

        public ContextImpl(long workerId, String displayName, ObjectConnection serverConnection, ServiceRegistry workerServices) {
            this.workerId = workerId;
            this.displayName = displayName;
            this.serverConnection = serverConnection;
            this.workerServices = workerServices;
        }

        @Override
        public Object getWorkerId() {
            return workerId;
        }

        @Override
        public String getDisplayName() {
            return displayName;
        }

        @Override
        public ClassLoader getApplicationClassLoader() {
            return ClassLoader.getSystemClassLoader();
        }

        @Override
        public ObjectConnection getServerConnection() {
            return serverConnection;
        }

        @Override
        public ServiceRegistry getServiceRegistry() {
            return workerServices;
        }
    }
}
