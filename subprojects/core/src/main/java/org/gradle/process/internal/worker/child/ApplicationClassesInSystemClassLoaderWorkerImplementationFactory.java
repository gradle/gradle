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
import org.gradle.api.JavaVersion;
import org.gradle.api.UncheckedIOException;
import org.gradle.api.internal.ClassPathRegistry;
import org.gradle.api.internal.file.TemporaryFileProvider;
import org.gradle.api.logging.LogLevel;
import org.gradle.internal.io.StreamByteBuffer;
import org.gradle.internal.jpms.ModuleDetection;
import org.gradle.internal.jvm.inspection.JvmVersionDetector;
import org.gradle.internal.process.ArgWriter;
import org.gradle.internal.remote.Address;
import org.gradle.internal.remote.internal.inet.MultiChoiceAddress;
import org.gradle.internal.remote.internal.inet.MultiChoiceAddressSerializer;
import org.gradle.internal.serialize.OutputStreamBackedEncoder;
import org.gradle.internal.stream.EncodedStream;
import org.gradle.process.ModulePathHandling;
import org.gradle.process.internal.JavaExecHandleBuilder;
import org.gradle.process.internal.worker.GradleWorkerMain;
import org.gradle.process.internal.worker.WorkerProcessBuilder;
import org.gradle.util.GUtil;

import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
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
public class ApplicationClassesInSystemClassLoaderWorkerImplementationFactory implements WorkerImplementationFactory {
    private final ClassPathRegistry classPathRegistry;
    private final TemporaryFileProvider temporaryFileProvider;
    private final JvmVersionDetector jvmVersionDetector;
    private final File gradleUserHomeDir;

    public ApplicationClassesInSystemClassLoaderWorkerImplementationFactory(ClassPathRegistry classPathRegistry, TemporaryFileProvider temporaryFileProvider, JvmVersionDetector jvmVersionDetector, File gradleUserHomeDir) {
        this.classPathRegistry = classPathRegistry;
        this.temporaryFileProvider = temporaryFileProvider;
        this.jvmVersionDetector = jvmVersionDetector;
        this.gradleUserHomeDir = gradleUserHomeDir;
    }

    @Override
    public void prepareJavaCommand(Object workerId, String displayName, WorkerProcessBuilder processBuilder, List<URL> implementationClassPath, List<URL> implementationModulePath, Address serverAddress, JavaExecHandleBuilder execSpec, boolean publishProcessInfo) {
        Collection<File> applicationClasspath = processBuilder.getApplicationClasspath();
        ModulePathHandling modulePathHandling = processBuilder.getModulePathHandling();
        LogLevel logLevel = processBuilder.getLogLevel();
        Set<String> sharedPackages = processBuilder.getSharedPackages();
        Object requestedSecurityManager = execSpec.getSystemProperties().get("java.security.manager");
        List<File> workerMainClassPath = classPathRegistry.getClassPath("WORKER_MAIN").getAsFiles();

        execSpec.setModulePathHandling(modulePathHandling);
        boolean useModulePath = modulePathHandling != ModulePathHandling.ALL_CLASSPATH;
        if (useModulePath) {
            execSpec.setMain("gradle.worker/worker." + GradleWorkerMain.class.getName());
        } else {
            execSpec.setMain("worker." + GradleWorkerMain.class.getName());
        }

        boolean useOptionsFile = shouldUseOptionsFile(execSpec);
        if (useOptionsFile) {
            // Use an options file to pass across application classpath
            File optionsFile = temporaryFileProvider.createTemporaryFile("gradle-worker-classpath", "txt");
            List<String> jvmArgs = writeOptionsFile(workerMainClassPath, implementationModulePath, applicationClasspath, modulePathHandling, optionsFile);
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
            if (useModulePath || implementationModulePath == null) {
                outstr.writeInt(implementationClassPath.size());
                for (URL entry : implementationClassPath) {
                    outstr.writeUTF(entry.toString());
                }
            } else {
                outstr.writeInt(implementationClassPath.size() + implementationModulePath.size());
                for (URL entry : implementationClassPath) {
                    outstr.writeUTF(entry.toString());
                }
                for (URL entry : implementationModulePath) {
                    outstr.writeUTF(entry.toString());
                }
            }

            // Serialize the worker config, this is consumed by SystemApplicationClassLoaderWorker
            OutputStreamBackedEncoder encoder = new OutputStreamBackedEncoder(outstr);
            encoder.writeSmallInt(logLevel.ordinal());
            encoder.writeBoolean(publishProcessInfo);
            encoder.writeString(gradleUserHomeDir.getAbsolutePath());
            new MultiChoiceAddressSerializer().write(encoder, (MultiChoiceAddress) serverAddress);

            // Serialize the worker, this is consumed by SystemApplicationClassLoaderWorker
            ActionExecutionWorker worker = new ActionExecutionWorker(processBuilder.getWorker(), workerId, displayName);
            byte[] serializedWorker = GUtil.serialize(worker);
            encoder.writeBinary(serializedWorker);

            encoder.flush();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        execSpec.setStandardInput(buffer.getInputStream());
    }

    private boolean shouldUseOptionsFile(JavaExecHandleBuilder execSpec) {
        JavaVersion executableVersion = jvmVersionDetector.getJavaVersion(execSpec.getExecutable());
        return executableVersion != null && executableVersion.isJava9Compatible();
    }

    private List<String> writeOptionsFile(Collection<File> workerMainClassPath, Collection<URL> implementationModulePath, Collection<File> applicationClasspath, ModulePathHandling modulePathHandling, File optionsFile) {
        List<File> classpath = new ArrayList<>();
        List<File> modulePath = new ArrayList<>();

        boolean useModulePath = modulePathHandling != ModulePathHandling.ALL_CLASSPATH;
        if (useModulePath) {
            modulePath.addAll(workerMainClassPath);
        } else {
            classpath.addAll(workerMainClassPath);
        }
        modulePath.addAll(ModuleDetection.inferModulePath(modulePathHandling, applicationClasspath));
        classpath.addAll(ModuleDetection.inferClasspath(modulePathHandling, applicationClasspath));

        if (useModulePath && implementationModulePath != null && !implementationModulePath.isEmpty()) {
            modulePath.addAll(implementationModulePath.stream().map(url -> new File(url.getFile())).collect(Collectors.toList()));
        }
        List<String> argumentList;
        if (useModulePath) {
            List<String> rootModules = ModuleDetection.toModuleNames(applicationClasspath);
            if (rootModules.isEmpty()) {
                argumentList = Arrays.asList("--module-path", Joiner.on(File.pathSeparator).join(modulePath),
                    "-cp", Joiner.on(File.pathSeparator).join(classpath));
            } else {
                argumentList = Arrays.asList("--module-path", Joiner.on(File.pathSeparator).join(modulePath),
                    "-cp", Joiner.on(File.pathSeparator).join(classpath),
                    "--add-modules", Joiner.on(",").join(rootModules));
            }

        } else {
            argumentList = Arrays.asList("-cp", Joiner.on(File.pathSeparator).join(classpath));
        }

        return ArgWriter.argsFileGenerator(optionsFile, ArgWriter.javaStyleFactory()).transform(argumentList);
    }
}
