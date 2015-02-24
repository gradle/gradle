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
import org.gradle.messaging.remote.Address;
import org.gradle.process.JavaExecSpec;
import org.gradle.process.internal.WorkerProcessBuilder;
import org.gradle.process.internal.launcher.GradleWorkerMain;
import org.gradle.util.GUtil;

import java.io.*;
import java.net.URL;
import java.util.Collection;
import java.util.List;

/**
 * A factory for a worker process which loads the application classes using the JVM's system ClassLoader.
 *
 * <p>Class loader hierarchy:</p>
 * <pre>
 *                            jvm bootstrap
 *                                 |
 *                +----------------+--------------+
 *                |                               |
 *            jvm system                      worker bootstrap
 *  (GradleWorkerMain, application) (SystemApplicationClassLoaderWorker, logging)
 *                |                   (ImplementationClassLoaderWorker)
 *                |                               |
 *             filter                          filter
 *        (shared packages)                  (logging)
 *                |                               |
 *                +---------------+---------------+
 *                                |
 *                          implementation
 *         (ActionExecutionWorker + worker action implementation)
 * </pre>
 */
public class ApplicationClassesInSystemClassLoaderWorkerFactory implements WorkerFactory {
    private final Object workerId;
    private final String displayName;
    private final WorkerProcessBuilder processBuilder;
    private final List<URL> implementationClassPath;
    private final Address serverAddress;
    private final ClassPathRegistry classPathRegistry;

    public ApplicationClassesInSystemClassLoaderWorkerFactory(Object workerId, String displayName, WorkerProcessBuilder processBuilder,
                                                              List<URL> implementationClassPath, Address serverAddress,
                                                              ClassPathRegistry classPathRegistry) {
        this.workerId = workerId;
        this.displayName = displayName;
        this.processBuilder = processBuilder;
        this.implementationClassPath = implementationClassPath;
        this.serverAddress = serverAddress;
        this.classPathRegistry = classPathRegistry;
    }

    public void prepareJavaCommand(JavaExecSpec execSpec) {
        execSpec.setMain("jarjar." + GradleWorkerMain.class.getName());
        execSpec.classpath(classPathRegistry.getClassPath("WORKER_MAIN").getAsFiles());
        Object requestedSecurityManager = execSpec.getSystemProperties().get("java.security.manager");
        if (requestedSecurityManager != null) {
            execSpec.systemProperty("org.gradle.security.manager", requestedSecurityManager);
        }
        execSpec.systemProperty("java.security.manager", "jarjar." + BootstrapSecurityManager.class.getName());
        Collection<URL> workerClassPath = classPathRegistry.getClassPath("WORKER_PROCESS").getAsURLs();
        ImplementationClassLoaderWorker worker = create();
        Collection<File> applicationClasspath = processBuilder.getApplicationClasspath();

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try {
            DataOutputStream outstr = new DataOutputStream(new EncodedStream.EncodedOutput(bytes));
            // Serialize the application classpath, this is consumed by BootstrapSecurityManager
            outstr.writeInt(applicationClasspath.size());
            for (File file : applicationClasspath) {
                outstr.writeUTF(file.getAbsolutePath());
            }
            // Serialize the worker process classpath, this is consumed by GradleWorkerMain
            outstr.writeInt(workerClassPath.size());
            for (URL entry : workerClassPath) {
                outstr.writeUTF(entry.toString());
            }
            // Serialize the worker, this is consumed by GradleWorkerMain
            byte[] serializedWorker = GUtil.serialize(worker);
            outstr.writeInt(serializedWorker.length);
            outstr.write(serializedWorker);
            outstr.flush();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        execSpec.setStandardInput(new ByteArrayInputStream(bytes.toByteArray()));
    }

    private ImplementationClassLoaderWorker create() {
        ActionExecutionWorker injectedWorker = new ActionExecutionWorker(processBuilder.getWorker(), workerId, displayName, serverAddress);
        return new ImplementationClassLoaderWorker(processBuilder.getLogLevel(), processBuilder.getSharedPackages(),
                implementationClassPath, injectedWorker);
    }

}
