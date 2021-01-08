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
import org.gradle.api.logging.LogLevel;
import org.gradle.initialization.GradleUserHomeDirProvider;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.concurrent.CompositeStoppable;
import org.gradle.internal.concurrent.DefaultExecutorFactory;
import org.gradle.internal.concurrent.ExecutorFactory;
import org.gradle.internal.event.DefaultListenerManager;
import org.gradle.internal.event.ListenerManager;
import org.gradle.internal.io.ClassLoaderObjectInputStream;
import org.gradle.internal.logging.LoggingManagerInternal;
import org.gradle.internal.logging.services.LoggingServiceRegistry;
import org.gradle.internal.nativeintegration.services.NativeServices;
import org.gradle.internal.remote.MessagingClient;
import org.gradle.internal.remote.ObjectConnection;
import org.gradle.internal.remote.internal.inet.MultiChoiceAddress;
import org.gradle.internal.remote.internal.inet.MultiChoiceAddressSerializer;
import org.gradle.internal.remote.services.MessagingServices;
import org.gradle.internal.serialize.Decoder;
import org.gradle.internal.serialize.InputStreamBackedDecoder;
import org.gradle.internal.service.DefaultServiceRegistry;
import org.gradle.internal.service.ServiceRegistry;
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

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.ObjectInputStream;
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

        Decoder decoder = new InputStreamBackedDecoder(configInputStream);

        // Read logging config and setup logging
        int logLevel = decoder.readSmallInt();
        LoggingServiceRegistry loggingServiceRegistry = LoggingServiceRegistry.newEmbeddableLogging();
        LoggingManagerInternal loggingManager = createLoggingManager(loggingServiceRegistry).setLevelInternal(LogLevel.values()[logLevel]);

        // Read whether process info should be published
        boolean shouldPublishJvmMemoryInfo = decoder.readBoolean();

        // Read path to Gradle user home
        String gradleUserHomeDirPath = decoder.readString();
        File gradleUserHomeDir = new File(gradleUserHomeDirPath);

        // Read server address and start connecting
        MultiChoiceAddress serverAddress = new MultiChoiceAddressSerializer().read(decoder);
        NativeServices.initialize(gradleUserHomeDir, false);
        DefaultServiceRegistry basicWorkerServices = new DefaultServiceRegistry(NativeServices.getInstance(), loggingServiceRegistry);
        basicWorkerServices.add(ExecutorFactory.class, new DefaultExecutorFactory());
        basicWorkerServices.addProvider(new MessagingServices());
        final WorkerServices workerServices = new WorkerServices(basicWorkerServices, gradleUserHomeDir);
        WorkerLogEventListener workerLogEventListener = new WorkerLogEventListener();
        workerServices.add(WorkerLogEventListener.class, workerLogEventListener);

        File workingDirectory = workerServices.get(WorkerDirectoryProvider.class).getWorkingDirectory();
        File errorLog = getLastResortErrorLogFile(workingDirectory);
        PrintUnrecoverableErrorToFileHandler unrecoverableErrorHandler = new PrintUnrecoverableErrorToFileHandler(errorLog);

        ObjectConnection connection = null;

        try {
            // Read serialized worker details
            final long workerId = decoder.readSmallLong();
            final String displayName = decoder.readString();
            byte[] serializedWorker = decoder.readBinary();
            Action<WorkerProcessContext> workerAction = deserializeWorker(serializedWorker);

            connection = basicWorkerServices.get(MessagingClient.class).getConnection(serverAddress);
            connection.addUnrecoverableErrorHandler(unrecoverableErrorHandler);
            configureLogging(loggingManager, connection, workerLogEventListener);
            // start logging now that the logging manager is connected
            loggingManager.start();
            if (shouldPublishJvmMemoryInfo) {
                configureWorkerJvmMemoryInfoEvents(workerServices, connection);
            }

            ActionExecutionWorker worker = new ActionExecutionWorker(workerAction);
            worker.execute(new ContextImpl(workerId, displayName, connection, workerServices));
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

    private Action<WorkerProcessContext> deserializeWorker(byte[] serializedWorker) {
        Action<WorkerProcessContext> action;
        try {
            ObjectInputStream instr = new ClassLoaderObjectInputStream(new ByteArrayInputStream(serializedWorker), getClass().getClassLoader());
            @SuppressWarnings("unchecked")
            Action<WorkerProcessContext> deserializedAction = (Action<WorkerProcessContext>) instr.readObject();
            action = deserializedAction;
        } catch (Exception e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
        return action;
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

    private void configureWorkerJvmMemoryInfoEvents(WorkerServices services, ObjectConnection connection) {
        connection.useParameterSerializers(WorkerJvmMemoryInfoSerializer.create());
        final WorkerJvmMemoryInfoProtocol workerJvmMemoryInfoProtocol = connection.addOutgoing(WorkerJvmMemoryInfoProtocol.class);
        services.get(MemoryManager.class).addListener(new JvmMemoryStatusListener() {
            @Override
            public void onJvmMemoryStatus(JvmMemoryStatus jvmMemoryStatus) {
                workerJvmMemoryInfoProtocol.sendJvmMemoryStatus(jvmMemoryStatus);
            }
        });
    }

    LoggingManagerInternal createLoggingManager(LoggingServiceRegistry loggingServiceRegistry) {
        LoggingManagerInternal loggingManagerInternal = loggingServiceRegistry.newInstance(LoggingManagerInternal.class);
        loggingManagerInternal.captureSystemSources();
        return loggingManagerInternal;
    }

    private static class WorkerServices extends DefaultServiceRegistry {
        public WorkerServices(ServiceRegistry parent, final File gradleUserHomeDir) {
            super(parent);
            addProvider(new Object() {
                GradleUserHomeDirProvider createGradleUserHomeDirProvider() {
                    return new GradleUserHomeDirProvider() {
                        @Override
                        public File getGradleUserHomeDirectory() {
                            return gradleUserHomeDir;
                        }
                    };
                }
            });
        }

        ListenerManager createListenerManager() {
            return new DefaultListenerManager(Global.class);
        }

        OsMemoryInfo createOsMemoryInfo() {
            return new DisabledOsMemoryInfo();
        }

        JvmMemoryInfo createJvmMemoryInfo() {
            return new DefaultJvmMemoryInfo();
        }

        MemoryManager createMemoryManager(OsMemoryInfo osMemoryInfo, JvmMemoryInfo jvmMemoryInfo, ListenerManager listenerManager, ExecutorFactory executorFactory) {
            return new DefaultMemoryManager(osMemoryInfo, jvmMemoryInfo, listenerManager, executorFactory);
        }

        WorkerDirectoryProvider createWorkerDirectoryProvider(GradleUserHomeDirProvider gradleUserHomeDirProvider) {
            return new DefaultWorkerDirectoryProvider(gradleUserHomeDirProvider);
        }
    }

    private static class ContextImpl implements WorkerProcessContext {
        private final long workerId;
        private final String displayName;
        private final ObjectConnection serverConnection;
        private final WorkerServices workerServices;

        public ContextImpl(long workerId, String displayName, ObjectConnection serverConnection, WorkerServices workerServices) {
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
