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

import com.google.common.base.Joiner;
import org.gradle.api.UncheckedIOException;
import org.gradle.api.internal.ClassPathRegistry;
import org.gradle.api.internal.file.TemporaryFileProvider;
import org.gradle.api.logging.LogLevel;
import org.gradle.internal.classpath.ClassPath;
import org.gradle.internal.jvm.Jvm;
import org.gradle.internal.process.ArgWriter;
import org.gradle.internal.serialize.OutputStreamBackedEncoder;
import org.gradle.internal.remote.Address;
import org.gradle.internal.remote.internal.inet.MultiChoiceAddress;
import org.gradle.internal.remote.internal.inet.MultiChoiceAddressSerializer;
import org.gradle.process.internal.worker.DefaultWorkerProcessBuilder;
import org.gradle.process.internal.JavaExecHandleBuilder;
import org.gradle.process.internal.worker.GradleWorkerMain;
import org.gradle.process.internal.streams.EncodedStream;
import org.gradle.util.GUtil;

import java.io.*;
import java.net.URL;
import java.util.*;

/**
 * A factory for a worker process which loads the application classes using the JVM's system ClassLoader.
 *
 * <p>Class loader hierarchy:</p>
 * <pre>
 *                       jvm bootstrap
 *                             |
 *                             |
 *                        jvm system
 *           (GradleWorkerMain, application classes)
 *                             |
 *                             |
 *                          filter
 *                    (shared packages)
 *                             |
 *                             |
 *                       implementation
 *          (SystemApplicationClassLoaderWorker, logging)
 *     (ActionExecutionWorker + worker action implementation)
 * </pre>
 */
public class ApplicationClassesInSystemClassLoaderWorkerFactory implements WorkerFactory {
    private final ClassPathRegistry classPathRegistry;
    private final TemporaryFileProvider temporaryFileProvider;

    public ApplicationClassesInSystemClassLoaderWorkerFactory(ClassPathRegistry classPathRegistry, TemporaryFileProvider temporaryFileProvider) {
        this.classPathRegistry = classPathRegistry;
        this.temporaryFileProvider = temporaryFileProvider;
    }

    @Override
    public void prepareJavaCommand(Object workerId, String displayName, DefaultWorkerProcessBuilder processBuilder, List<URL> implementationClassPath, Address serverAddress, JavaExecHandleBuilder execSpec) {
        Collection<File> applicationClasspath = processBuilder.getApplicationClasspath();
        LogLevel logLevel = processBuilder.getLogLevel();
        Set<String> sharedPackages = processBuilder.getSharedPackages();
        Object requestedSecurityManager = execSpec.getSystemProperties().get("java.security.manager");
        ClassPath workerMainClassPath = classPathRegistry.getClassPath("WORKER_MAIN");

        execSpec.setMain("worker." + GradleWorkerMain.class.getName());

        // This check is not quite right. Should instead probe the version of the requested executable and use options file if it is Java 9 or later, regardless of
        // the version of this JVM
        boolean useOptionsFile = Jvm.current().getJavaVersion().isJava9Compatible() && execSpec.getExecutable().equals(Jvm.current().getJavaExecutable().getPath());
        if (useOptionsFile) {
            // Use an options file to pass across application classpath
            File optionsFile = temporaryFileProvider.createTemporaryFile("gradle-worker-classpath", "txt");
            List<String> jvmArgs = writeOptionsFile(workerMainClassPath.getAsFiles(), applicationClasspath, optionsFile);
            execSpec.jvmArgs(jvmArgs);
        } else {
            // Use a dummy security manager, which hacks the application classpath into the system ClassLoader
            execSpec.classpath(workerMainClassPath.getAsFiles());
            execSpec.systemProperty("java.security.manager", "worker." + BootstrapSecurityManager.class.getName());
        }

        // Serialize configuration for the worker process to it stdin

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try {
            DataOutputStream outstr = new DataOutputStream(new EncodedStream.EncodedOutput(bytes));
            if (!useOptionsFile) {
                // Serialize the application classpath, this is consumed by BootstrapSecurityManager
                outstr.writeInt(applicationClasspath.size());
                for (File file : applicationClasspath) {
                    outstr.writeUTF(file.getAbsolutePath());
                }
                // Serialize the actual security manager type, this is consumed by BootstrapSecurityManager
                outstr.writeUTF(requestedSecurityManager == null ? "" : requestedSecurityManager.toString());
            }

            // Serialize the shared packages, this is consumed by GradleWorkerMain
            outstr.writeInt(sharedPackages.size());
            for (String str : sharedPackages) {
                outstr.writeUTF(str);
            }

            // Serialize the worker implementation classpath, this is consumed by GradleWorkerMain
            outstr.writeInt(implementationClassPath.size());
            for (URL entry : implementationClassPath) {
                outstr.writeUTF(entry.toString());
            }

            // Serialize the worker config, this is consumed by SystemApplicationClassLoaderWorker
            OutputStreamBackedEncoder encoder = new OutputStreamBackedEncoder(outstr);
            encoder.writeSmallInt(logLevel.ordinal());
            new MultiChoiceAddressSerializer().write(encoder, (MultiChoiceAddress) serverAddress);

            // Serialize the worker, this is consumed by SystemApplicationClassLoaderWorker
            ActionExecutionWorker worker = new ActionExecutionWorker(processBuilder.getWorker(), workerId, displayName, processBuilder.getGradleUserHomeDir());
            byte[] serializedWorker = GUtil.serialize(worker);
            encoder.writeBinary(serializedWorker);

            encoder.flush();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        byte[] encodedConfig = bytes.toByteArray();
        execSpec.setStandardInput(new ByteArrayInputStream(encodedConfig));
    }

    private List<String> writeOptionsFile(Collection<File> workerMainClassPath, Collection<File> applicationClasspath, File optionsFile) {
        List<File> classpath = new ArrayList<File>(workerMainClassPath.size() + applicationClasspath.size());
        classpath.addAll(workerMainClassPath);
        classpath.addAll(applicationClasspath);
        return ArgWriter.argsFileGenerator(optionsFile, ArgWriter.unixStyleFactory()).transform(Arrays.asList("-cp", Joiner.on(File.pathSeparator).join(classpath)));
    }
}
