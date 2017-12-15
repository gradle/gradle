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

import org.gradle.api.internal.tasks.testing.*;
import org.gradle.api.tasks.testing.TestOutputEvent;
import org.gradle.api.tasks.testing.TestResult;
import org.gradle.process.JavaForkOptions;
import org.gradle.workers.IsolationMode;
import org.gradle.workers.WorkerExecutor;
import org.junit.platform.launcher.TestIdentifier;

import java.io.EOFException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.channels.Channels;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class JUnitPlatformTestExecutor implements TestExecuter<JUnitPlatformTestExecutionSpec> {
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
                config.setIsolationMode(IsolationMode.PROCESS);
                config.params(testExecutionSpec.getOptions(), server.socket().getLocalPort());
                config.setClasspath(testExecutionSpec.getClasspath());
                forkOptions.copyTo(config.getForkOptions());
            });

            new Thread(new EventHandler(server.accept(), testResultProcessor)).start();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static class EventHandler implements Runnable {
        private final Map<String, TestDescriptorInternal> descriptorCache = new ConcurrentHashMap<>();
        private final SocketChannel socket;
        private final TestResultProcessor testResultProcessor;

        public EventHandler(SocketChannel socket, TestResultProcessor testResultProcessor) {
            this.socket = socket;
            this.testResultProcessor = testResultProcessor;
        }

        @Override
        public void run() {
            try (ObjectInputStream stream = new ObjectInputStream(Channels.newInputStream(socket))) {
                Object obj = stream.readObject();
                while (obj != null) {
                    JUnitPlatformEvent event = (JUnitPlatformEvent) obj;
                    TestDescriptorInternal test = getDescriptor(event.getTest());
                    switch (event.getType()) {
                        case START:
                            Object parentId = Optional.ofNullable(test.getParent())
                                .map(TestDescriptorInternal::getId)
                                .orElse(null);
                            testResultProcessor.started(test, new TestStartEvent(event.getTime(), parentId));
                            break;
                        case OUTPUT:
                            // TODO differentiate between err and out
                            testResultProcessor.output(test.getId(), new DefaultTestOutputEvent(TestOutputEvent.Destination.StdOut, event.getMessage()));
                            break;
                        case SKIPPED:
                            testResultProcessor.completed(test.getId(), new TestCompleteEvent(event.getTime(), TestResult.ResultType.SKIPPED));
                            break;
                        case FAILED:
                            testResultProcessor.failure(test.getId(), event.getError());
                            testResultProcessor.completed(test.getId(), new TestCompleteEvent(event.getTime(), TestResult.ResultType.FAILURE));
                            break;
                        case SUCCEEDED:
                            testResultProcessor.completed(test.getId(), new TestCompleteEvent(event.getTime(), TestResult.ResultType.SUCCESS));
                            break;
                    }
                    obj = stream.readObject();
                }
            } catch (EOFException e) {
                // test execution is done
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            } catch (ClassNotFoundException e) {
                // TODO better exception
                throw new RuntimeException(e);
            }
        }

        private TestDescriptorInternal getDescriptor(TestIdentifier test) {
            return descriptorCache.computeIfAbsent(test.getUniqueId(), id -> {
                TestDescriptorInternal parent = test.getParentId()
                    .map(descriptorCache::get)
                    .orElse(null);
                return new JUnitPlatformTestDescriptor(test, parent);
            });
        }
    }

    private ServerSocketChannel startServer() {
        try {
            InetSocketAddress addr = new InetSocketAddress(InetAddress.getLoopbackAddress(), 0);
            ServerSocketChannel server = ServerSocketChannel.open();
            server.bind(addr);
            return server;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
