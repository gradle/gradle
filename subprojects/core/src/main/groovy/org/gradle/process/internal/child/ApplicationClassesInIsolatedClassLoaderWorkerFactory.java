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

package org.gradle.process.internal.child;

import org.gradle.api.UncheckedIOException;
import org.gradle.api.internal.ClassPathRegistry;
import org.gradle.internal.classpath.DefaultClassPath;
import org.gradle.messaging.remote.Address;
import org.gradle.process.JavaExecSpec;
import org.gradle.process.internal.WorkerProcessBuilder;
import org.gradle.process.internal.launcher.IsolatedGradleWorkerMain;
import org.gradle.util.GUtil;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.util.Collection;

/**
 * A factory for a worker process which loads application classes using an isolated ClassLoader.
 *
 * <p>Class loader hierarchy:</p>
 * <pre>
 *                              jvm bootstrap
 *                                   |
 *                   +---------------+----------------+
 *                   |                                |
 *               jvm system                       application
 *  (ImplementationClassLoaderWorker, logging)        |
 *                   |                                |
 *                filter                           filter
 *              (logging)                     (shared packages)
 *                   |                                |
 *                   +--------------+-----------------+
 *                                  |
 *                            implementation
 *                (ActionExecutionWorker + action implementation)
 * </pre>
 *
 */
public class ApplicationClassesInIsolatedClassLoaderWorkerFactory implements WorkerFactory {
    private final Object workerId;
    private final String displayName;
    private final WorkerProcessBuilder processBuilder;
    private final Collection<URL> implementationClassPath;
    private final Address serverAddress;
    private final ClassPathRegistry classPathRegistry;

    public ApplicationClassesInIsolatedClassLoaderWorkerFactory(Object workerId, String displayName, WorkerProcessBuilder processBuilder,
                                            Collection<URL> implementationClassPath, Address serverAddress,
                                            ClassPathRegistry classPathRegistry) {
        this.workerId = workerId;
        this.displayName = displayName;
        this.processBuilder = processBuilder;
        this.implementationClassPath = implementationClassPath;
        this.serverAddress = serverAddress;
        this.classPathRegistry = classPathRegistry;
    }

    public void prepareJavaCommand(JavaExecSpec execSpec) {
        execSpec.setMain(IsolatedGradleWorkerMain.class.getName());
        execSpec.classpath(classPathRegistry.getClassPath("WORKER_PROCESS").getAsFiles());
        Collection<URI> applicationClassPath = new DefaultClassPath(processBuilder.getApplicationClasspath()).getAsURIs();

        // Write configuration to stdin. This is consumed by IsolatedGradleWorkerMain
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try {
            DataOutputStream outstr = new DataOutputStream(new EncodedStream.EncodedOutput(bytes));
            // Write application classpath
            outstr.writeInt(applicationClassPath.size());
            for (URI entry : applicationClassPath) {
                outstr.writeUTF(entry.toString());
            }
            // Write serialized worker
            GUtil.serialize(create(), outstr);
            outstr.flush();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        execSpec.setStandardInput(new ByteArrayInputStream(bytes.toByteArray()));
    }

    private ImplementationClassLoaderWorker create() {
        ActionExecutionWorker injectedWorker = new ActionExecutionWorker(processBuilder.getWorker(), workerId,
                displayName, serverAddress, processBuilder.getGradleUserHomeDir());
        return new ImplementationClassLoaderWorker(processBuilder.getLogLevel(),
                processBuilder.getSharedPackages(), implementationClassPath, GUtil.serialize(injectedWorker));
    }
}
