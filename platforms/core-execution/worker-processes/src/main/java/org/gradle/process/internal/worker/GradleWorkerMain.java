/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.process.internal.worker;

import org.gradle.internal.classloader.FilteringClassLoader;
import org.gradle.internal.stream.EncodedStream;

import java.io.BufferedWriter;
import java.io.DataInputStream;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

/**
 * The main entry point for a worker process that is using the system ClassLoader strategy. Reads worker configuration and a serialized worker action from stdin,
 * sets up the worker ClassLoader, and then delegates to {@link org.gradle.process.internal.worker.child.SystemApplicationClassLoaderWorker} to deserialize and execute the action.
 */
public class GradleWorkerMain {
    public void run() throws Exception {
        DataInputStream instr = new DataInputStream(new EncodedStream.EncodedInput(System.in));

        // Read shared packages
        int sharedPackagesCount = instr.readInt();
        List<String> sharedPackages = new ArrayList<String>(sharedPackagesCount);
        for (int i = 0; i < sharedPackagesCount; i++) {
            sharedPackages.add(instr.readUTF());
        }

        // Read worker implementation classpath
        int classPathLength = instr.readInt();
        URL[] implementationClassPath = new URL[classPathLength];
        for (int i = 0; i < classPathLength; i++) {
            String url = instr.readUTF();
            implementationClassPath[i] = new URL(url);
        }

        ClassLoader implementationClassLoader;
        if (classPathLength > 0) {
            // Set up worker ClassLoader
            FilteringClassLoader.Spec filteringClassLoaderSpec = new FilteringClassLoader.Spec();
            for (String sharedPackage : sharedPackages) {
                filteringClassLoaderSpec.allowPackage(sharedPackage);
            }
            FilteringClassLoader filteringClassLoader = new FilteringClassLoader(getClass().getClassLoader(), filteringClassLoaderSpec);
            implementationClassLoader = new URLClassLoader(implementationClassPath, filteringClassLoader);
        } else {
            // If no implementation classpath has been provided, just use the application classloader
            implementationClassLoader = getClass().getClassLoader();
        }

        // Write out all URLs in implementation classpath to /tmp/worker-classpath.txt
        OutputStreamWriter writer = new OutputStreamWriter(
            new FileOutputStream("/tmp/worker-classpath.txt")
        );
        for (URL url : implementationClassPath) {
            writer.write(url.toString() + "\n");
        }
        writer.flush();
        writer.close();

        @SuppressWarnings("unchecked")
        Class<? extends Callable<Void>> workerClass = (Class<? extends Callable<Void>>) implementationClassLoader.loadClass("org.gradle.process.internal.worker.child.SystemApplicationClassLoaderWorker").asSubclass(Callable.class);
        Callable<Void> main = workerClass.getConstructor(DataInputStream.class).newInstance(instr);
        main.call();
    }

    public static void main(String[] args) {
        try {
            new GradleWorkerMain().run();
            System.exit(0);
        } catch (Throwable throwable) {
            throwable.printStackTrace(System.err);
            System.exit(1);
        }
    }
}
