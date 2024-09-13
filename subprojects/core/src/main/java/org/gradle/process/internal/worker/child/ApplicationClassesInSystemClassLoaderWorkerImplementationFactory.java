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
import org.gradle.api.internal.file.temp.TemporaryFileProvider;
import org.gradle.api.logging.LogLevel;
import org.gradle.internal.io.StreamByteBuffer;
import org.gradle.internal.process.ArgWriter;
import org.gradle.internal.remote.Address;
import org.gradle.internal.remote.internal.inet.MultiChoiceAddress;
import org.gradle.internal.serialize.OutputStreamBackedEncoder;
import org.gradle.internal.stream.EncodedStream;
import org.gradle.process.internal.JavaExecHandleBuilder;
import org.gradle.process.internal.worker.GradleWorkerMain;
import org.gradle.process.internal.worker.WorkerProcessBuilder;
import org.gradle.process.internal.worker.messaging.WorkerConfig;
import org.gradle.process.internal.worker.messaging.WorkerConfigSerializer;

import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

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
public class ApplicationClassesInSystemClassLoaderWorkerImplementationFactory {
    private final ClassPathRegistry classPathRegistry;
    private final TemporaryFileProvider temporaryFileProvider;
    private final File gradleUserHomeDir;

    public ApplicationClassesInSystemClassLoaderWorkerImplementationFactory(
        ClassPathRegistry classPathRegistry,
        TemporaryFileProvider temporaryFileProvider,
        File gradleUserHomeDir
    ) {
        this.classPathRegistry = classPathRegistry;
        this.temporaryFileProvider = temporaryFileProvider;
        this.gradleUserHomeDir = gradleUserHomeDir;
    }

    /**
     * Configures the Java command that will be used to launch the child process.
     */
    public void prepareJavaCommand(long workerId, String displayName, WorkerProcessBuilder processBuilder, List<URL> implementationClassPath, List<URL> implementationModulePath, Address serverAddress, JavaExecHandleBuilder execSpec, boolean publishProcessInfo, boolean useOptionsFile) {
        Collection<File> applicationClasspath = processBuilder.getApplicationClasspath();
        Set<File> applicationModulePath = processBuilder.getApplicationModulePath();
        LogLevel logLevel = processBuilder.getLogLevel();
        Set<String> sharedPackages = processBuilder.getSharedPackages();
        Object requestedSecurityManager = execSpec.getSystemProperties().get().get("java.security.manager");
        List<File> workerMainClassPath = classPathRegistry.getClassPath("WORKER_MAIN").getAsFiles();

        boolean runAsModule = !applicationModulePath.isEmpty() && execSpec.getModularity().getInferModulePath().get();

        if (runAsModule) {
            execSpec.getMainModule().set("gradle.worker");
        }
        execSpec.getMainClass().set("worker." + GradleWorkerMain.class.getName());
        if (useOptionsFile) {
            // Use an options file to pass across application classpath
            File optionsFile = temporaryFileProvider.createTemporaryFile("gradle-worker-classpath", "txt");
            List<String> jvmArgs = writeOptionsFile(runAsModule, workerMainClassPath, implementationModulePath, applicationClasspath, applicationModulePath, optionsFile);
            execSpec.jvmArgs(jvmArgs);
        } else {
            // Use a dummy security manager, which hacks the application classpath into the system ClassLoader
            execSpec.classpath(workerMainClassPath);
            execSpec.systemProperty("java.security.manager", "worker." + BootstrapSecurityManager.class.getName());
        }

        // Serialize configuration for the worker process to it stdin

        StreamByteBuffer buffer = new StreamByteBuffer();
        try {
            DataOutputStream outstr = new DataOutputStream(new EncodedStream.EncodedOutput(buffer.getOutputStream()));
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
            if (runAsModule || implementationModulePath == null) {
                outstr.writeInt(implementationClassPath.size());
                for (URL entry : implementationClassPath) {
                    outstr.writeUTF(entry.toString());
                }
                // We do not serialize the module path. Instead, implementation modules are directly added to the application module path when
                // starting the worker process. Implementation modules are hidden to the application modules by module visibility.
            } else {
                outstr.writeInt(implementationClassPath.size() + implementationModulePath.size());
                for (URL entry : implementationClassPath) {
                    outstr.writeUTF(entry.toString());
                }
                for (URL entry : implementationModulePath) {
                    outstr.writeUTF(entry.toString());
                }
            }

            WorkerConfig config = new WorkerConfig(
                logLevel,
                publishProcessInfo,
                gradleUserHomeDir.getAbsolutePath(),
                (MultiChoiceAddress) serverAddress,
                workerId,
                displayName,
                processBuilder.getWorker(),
                processBuilder.getNativeServicesMode()
            );

            // Serialize the worker config, this is consumed by SystemApplicationClassLoaderWorker
            OutputStreamBackedEncoder encoder = new OutputStreamBackedEncoder(outstr);
            try {
                new WorkerConfigSerializer().write(encoder, config);
            } finally {
                encoder.flush();
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        execSpec.setStandardInput(buffer.getInputStream());
    }

    private List<String> writeOptionsFile(boolean runAsModule, Collection<File> workerMainClassPath, Collection<URL> implementationModulePath, Collection<File> applicationClasspath, Set<File> applicationModulePath, File optionsFile) {
        List<File> classpath = new ArrayList<>();
        List<File> modulePath = new ArrayList<>();

        if (runAsModule) {
            modulePath.addAll(workerMainClassPath);
        } else {
            classpath.addAll(workerMainClassPath);
        }
        modulePath.addAll(applicationModulePath);
        classpath.addAll(applicationClasspath);

        if (!modulePath.isEmpty() && implementationModulePath != null && !implementationModulePath.isEmpty()) {
            // We add the implementation module path as well, as we do not load modules dynamically through a separate class loader in the worker.
            // This acceptable, because the implementation modules are hidden to the application by module visibility.
            modulePath.addAll(implementationModulePath.stream().map(url -> {
                try {
                    return new File(url.toURI());
                } catch (URISyntaxException e) {
                    throw new RuntimeException(e);
                }
            }).collect(Collectors.toList()));
        }
        List<String> argumentList = new ArrayList<>();
        if (!modulePath.isEmpty()) {
            argumentList.addAll(Arrays.asList("--module-path", Joiner.on(File.pathSeparator).join(modulePath), "--add-modules", "ALL-MODULE-PATH"));
        }
        if (!classpath.isEmpty()) {
            argumentList.addAll(Arrays.asList("-cp", Joiner.on(File.pathSeparator).join(classpath)));
        }
        return ArgWriter.argsFileGenerator(optionsFile, ArgWriter.javaStyleFactory()).transform(argumentList);
    }
}
