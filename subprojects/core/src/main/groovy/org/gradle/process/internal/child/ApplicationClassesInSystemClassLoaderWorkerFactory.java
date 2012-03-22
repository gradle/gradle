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
import org.gradle.process.internal.WorkerProcessBuilder;
import org.gradle.process.internal.launcher.BootstrapClassLoaderWorker;
import org.gradle.util.GUtil;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.jar.Attributes;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

/**
 * A factory for a worker process which loads the application classes using the JVM's system ClassLoader.
 *
 * <p>Class loader hierarchy:</p>
 * <pre>
 *                              bootstrap
 *                                 |
 *                +----------------+--------------+
 *                |                               |
 *              system                      worker bootstrap
 *  (GradleWorkerMain, application) (SystemApplicationClassLoaderWorker, logging)
 *                |                   (ImplementationClassLoaderWorker)
 *                |                               |
 *             filter                          filter
 *        (shared packages)                  (logging)
 *                |                              |
 *                +---------------+--------------+
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
    private final File classpathJarFile;

    public ApplicationClassesInSystemClassLoaderWorkerFactory(Object workerId, String displayName, WorkerProcessBuilder processBuilder,
                                                              List<URL> implementationClassPath, Address serverAddress,
                                                              ClassPathRegistry classPathRegistry) {
        this.workerId = workerId;
        this.displayName = displayName;
        this.processBuilder = processBuilder;
        this.implementationClassPath = implementationClassPath;
        this.serverAddress = serverAddress;
        this.classPathRegistry = classPathRegistry;
        classpathJarFile = createClasspathJarFile(processBuilder);
    }

    private File createClasspathJarFile(WorkerProcessBuilder processBuilder) {
        try {
            File classpathJarFile = File.createTempFile("GradleWorkerProcess", "classpath.jar");
            new ClasspathJarFactory().createClasspathJarFile(classpathJarFile, processBuilder.getApplicationClasspath());
            classpathJarFile.deleteOnExit();
            return classpathJarFile;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public Collection<File> getSystemClasspath() {
        List<File> systemClasspath = new ArrayList<File>();
        systemClasspath.addAll(classPathRegistry.getClassPath("WORKER_MAIN").getAsFiles());
        systemClasspath.add(classpathJarFile);
        return systemClasspath;
    }

    public Callable<?> create() {
        // Serialize the bootstrap worker, so it can be transported through the system ClassLoader
        ActionExecutionWorker injectedWorker = new ActionExecutionWorker(processBuilder.getWorker(), workerId, displayName, serverAddress);
        ImplementationClassLoaderWorker worker = new ImplementationClassLoaderWorker(processBuilder.getLogLevel(), processBuilder.getSharedPackages(),
                implementationClassPath, injectedWorker);
        byte[] serializedWorker = GUtil.serialize(worker);

        return new BootstrapClassLoaderWorker(classPathRegistry.getClassPath("WORKER_PROCESS").getAsURLs(), processBuilder.getApplicationClasspath(), serializedWorker);
    }

    /**
     * Creates an empty jar file that contains a manifest with a Class-path entry that will load the supplied classpath.
     * This can be used to circumvent command-line length limit on windows when the classpath is very long.
     * Note that the main class must be placed on the classpath explicitly, and cannot be loaded via a classpath jar.
     */
    private static class ClasspathJarFactory {
        public void createClasspathJarFile(File file, Collection<File> classpath) throws IOException {
            Manifest manifest = new Manifest();
            Attributes attributes = manifest.getMainAttributes();
            attributes.put(Attributes.Name.MANIFEST_VERSION, "1.0");
            attributes.putValue("Class-Path", GUtil.join(classpath, " "));
            writeManifestOnlyJarFile(file, manifest);
        }

        private void writeManifestOnlyJarFile(File file, Manifest manifest) throws IOException {
            JarOutputStream jarOutputStream = new JarOutputStream(new FileOutputStream(file), manifest);
            jarOutputStream.close();
        }
    }

}
