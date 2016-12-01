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
import org.gradle.internal.UncheckedException;
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
import org.gradle.process.internal.worker.WorkerLoggingSerializer;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.ObjectInputStream;
import java.util.concurrent.Callable;

/**
 * <p>Stage 2 of the start-up for a worker process with the application classes loaded in the system ClassLoader. Takes
 * care of deserializing and invoking the worker action.</p>
 *
 * <p> Instantiated in the implementation ClassLoader and invoked from {@link org.gradle.process.internal.worker.GradleWorkerMain}.
 * See {@link ApplicationClassesInSystemClassLoaderWorkerFactory} for details.</p>
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

        // Read server address and start connecting
        MultiChoiceAddress serverAddress = new MultiChoiceAddressSerializer().read(decoder);
        MessagingServices messagingServices = createClient();

        try {
            final ObjectConnection connection = messagingServices.get(MessagingClient.class).getConnection(serverAddress);
            configureLogging(loggingManager, connection);

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
                action.execute(new WorkerContext() {
                    public ClassLoader getApplicationClassLoader() {
                        return ClassLoader.getSystemClassLoader();
                    }

                    @Override
                    public ObjectConnection getServerConnection() {
                        return connection;
                    }
                });
            } finally {
                connection.stop();
            }
        } finally {
            messagingServices.close();
        }

        return null;
    }

    private void configureLogging(LoggingManagerInternal loggingManager, ObjectConnection connection) {
        connection.useParameterSerializers(WorkerLoggingSerializer.create());
        WorkerLoggingProtocol workerLoggingProtocol = connection.addOutgoing(WorkerLoggingProtocol.class);
        loggingManager.addOutputEventListener(new WorkerLogEventListener(workerLoggingProtocol));
    }

    MessagingServices createClient() {
        return new MessagingServices();
    }

    LoggingManagerInternal createLoggingManager() {
        LoggingManagerInternal loggingManagerInternal = LoggingServiceRegistry.newEmbeddableLogging().newInstance(LoggingManagerInternal.class);
        loggingManagerInternal.captureSystemSources();
        return loggingManagerInternal;
    }
}
