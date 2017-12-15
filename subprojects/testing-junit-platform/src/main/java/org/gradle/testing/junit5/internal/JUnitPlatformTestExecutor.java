/*
 * Copyright 2017 the original author or authors.
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
package org.gradle.testing.junit5.internal;

import org.gradle.api.internal.tasks.testing.TestExecuter;
import org.gradle.api.internal.tasks.testing.TestResultProcessor;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.process.JavaForkOptions;
import org.gradle.workers.WorkerExecutor;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.channels.Channels;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;

public class JUnitPlatformTestExecutor implements TestExecuter<JUnitPlatformTestExecutionSpec> {
    private static final Logger LOGGER = Logging.getLogger(JUnitPlatformTestExecutor.class);

    private final WorkerExecutor workerExecutor;
    private final JavaForkOptions forkOptions;

    public JUnitPlatformTestExecutor(WorkerExecutor workerExecutor, JavaForkOptions forkOptions) {
        this.workerExecutor = workerExecutor;
        this.forkOptions = forkOptions;
    }

    @Override
    public void execute(final JUnitPlatformTestExecutionSpec testExecutionSpec, TestResultProcessor testResultProcessor) {
        try (ServerSocketChannel server = startServer()) {
            workerExecutor.submit(JUnitPlatformLauncher.class, config -> {
                config.params(testExecutionSpec.getOptions(), server.socket().getLocalPort());
                config.setClasspath(testExecutionSpec.getClasspath());
                forkOptions.copyTo(config.getForkOptions());
            });

            handleEvents(server.accept(), testResultProcessor);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void handleEvents(SocketChannel socket, TestResultProcessor testResultProcessor) {
        try (ObjectInputStream stream = new ObjectInputStream(Channels.newInputStream(socket))) {
            Object obj = stream.readObject();
            while (obj != null) {
                if (obj instanceof JUnitPlatformEvent.Started) {
                    JUnitPlatformEvent.Started event = (JUnitPlatformEvent.Started) obj;
                    testResultProcessor.started(event.getTest(), event.getEvent());
                } else if (obj instanceof JUnitPlatformEvent.Completed) {
                    JUnitPlatformEvent.Completed event = (JUnitPlatformEvent.Completed) obj;
                    testResultProcessor.completed(event.getTest().getId(), event.getEvent());
                } else if (obj instanceof JUnitPlatformEvent.Output) {
                    JUnitPlatformEvent.Output event = (JUnitPlatformEvent.Output) obj;
                    testResultProcessor.output(event.getTest().getId(), event.getEvent());
                } else if (obj instanceof JUnitPlatformEvent.Failure) {
                    JUnitPlatformEvent.Failure event = (JUnitPlatformEvent.Failure) obj;
                    testResultProcessor.failure(event.getTest().getId(), event.getResult());
                } else {
                    LOGGER.debug("Unknown JUnit Platform event: {}", obj);
                }

                obj = stream.readObject();
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (ClassNotFoundException e) {
            // TODO better exception
            throw new RuntimeException(e);
        }
    }

    private ServerSocketChannel startServer() {
        try {
            InetSocketAddress addr = new InetSocketAddress(0);
            ServerSocketChannel server = ServerSocketChannel.open();
            server.bind(addr);
            return server;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
