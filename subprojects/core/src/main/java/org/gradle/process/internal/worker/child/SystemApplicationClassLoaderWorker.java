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
import org.gradle.internal.concurrent.ExecutorFactory;
import org.gradle.internal.event.DefaultListenerManager;
import org.gradle.internal.event.ListenerManager;
import org.gradle.internal.io.ClassLoaderObjectInputStream;
import org.gradle.internal.logging.LoggingManagerInternal;
import org.gradle.internal.logging.services.LoggingServiceRegistry;
import org.gradle.internal.remote.MessagingClient;
import org.gradle.internal.remote.ObjectConnection;
import org.gradle.internal.remote.internal.inet.MultiChoiceAddress;
import org.gradle.internal.remote.internal.inet.MultiChoiceAddressSerializer;
import org.gradle.internal.remote.services.MessagingServices;
import org.gradle.internal.serialize.Decoder;
import org.gradle.internal.serialize.InputStreamBackedDecoder;
import org.gradle.internal.service.DefaultServiceRegistry;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.internal.time.Clock;
import org.gradle.internal.time.Time;
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

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.ObjectInputStream;
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

    public Void call() throws Exception {
        if (System.getProperty("org.gradle.worker.test.stuck") != null) {
            // Simulate a stuck worker. There's probably a way to inject this failure...
            Thread.sleep(30000);
            return null;
        }

        Decoder decoder = new InputStreamBackedDecoder(configInputStream);

        // Read logging config and setup logging
        int logLevel = decoder.readSmallInt();
        LoggingManagerInternal loggingManager = createLoggingManager();
        loggingManager.setLevelInternal(LogLevel.values()[logLevel]).start();

        // Read whether process info should be published
        boolean shouldPublishJvmMemoryInfo = decoder.readBoolean();

        // Read path to Gradle user home
        String gradleUserHomeDirPath = decoder.readString();
        File gradleUserHomeDir = new File(gradleUserHomeDirPath);

        // Read server address and start connecting
        MultiChoiceAddress serverAddress = new MultiChoiceAddressSerializer().read(decoder);
        MessagingServices messagingServices = new MessagingServices();
        final WorkerServices workerServices = new WorkerServices(messagingServices, gradleUserHomeDir);

        ObjectConnection connection = null;
        WorkerLogEventListener workerLogEventListener = null;
        try {
            // Read serialized worker
            byte[] serializedWorker = decoder.readBinary();

            // Deserialize the worker action
            Action<WorkerContext> action;
            try {
                ObjectInputStream instr = new ClassLoaderObjectInputStream(new ByteArrayInputStream(serializedWorker), getClass().getClassLoader());
                action = (Action<WorkerContext>) instr.readObject();
            } catch (Exception e) {
                throw UncheckedException.throwAsUncheckedException(e);
            }

            connection = messagingServices.get(MessagingClient.class).getConnection(serverAddress);
            workerLogEventListener = configureLogging(loggingManager, connection);
            if (shouldPublishJvmMemoryInfo) {
                configureWorkerJvmMemoryInfoEvents(workerServices, connection);
            }

            final ObjectConnection serverConnection = connection;
            action.execute(new WorkerContext() {
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
            });
        } finally {
            if (workerLogEventListener != null) {
                loggingManager.removeOutputEventListener(workerLogEventListener);
            }
            if (connection != null) {
                connection.stop();
            }
            messagingServices.close();
            loggingManager.stop();
        }

        return null;
    }

    private WorkerLogEventListener configureLogging(LoggingManagerInternal loggingManager, ObjectConnection connection) {
        connection.useParameterSerializers(WorkerLoggingSerializer.create());
        WorkerLoggingProtocol workerLoggingProtocol = connection.addOutgoing(WorkerLoggingProtocol.class);
        WorkerLogEventListener workerLogEventListener = new WorkerLogEventListener(workerLoggingProtocol);
        loggingManager.addOutputEventListener(workerLogEventListener);
        return workerLogEventListener;
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

    LoggingManagerInternal createLoggingManager() {
        LoggingManagerInternal loggingManagerInternal = LoggingServiceRegistry.newEmbeddableLogging().newInstance(LoggingManagerInternal.class);
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

        Clock createClock() {
            return Time.clock();
        }

        ListenerManager createListenerManager() {
            return new DefaultListenerManager();
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
}
