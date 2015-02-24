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

package org.gradle.process.internal.launcher;

import org.gradle.process.internal.child.EncodedStream;

import java.io.DataInputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * The main entry point for a worker process that is using the system ClassLoader strategy. Reads worker configuration and a serialized worker action from stdin,
 * sets up the worker ClassLoader, and then delegates to {@link org.gradle.process.internal.child.SystemApplicationClassLoaderWorker} to deserialize and execute the action.
 */
public class GradleWorkerMain {
    public void run() throws Exception {
        DataInputStream instr = new DataInputStream(new EncodedStream.EncodedInput(System.in));

        // Read infrastructure classpath
        int classPathLength = instr.readInt();
        URL[] infrastructureClassPath = new URL[classPathLength];
        for (int i = 0; i < classPathLength; i++) {
            String url = instr.readUTF();
            infrastructureClassPath[i] = new URL(url);
        }

        // Read worker configuration
        int logLevel = instr.readInt();
        int sharedPackagesCount = instr.readInt();
        List<String> sharedPackages = new ArrayList<String>(sharedPackagesCount);
        for (int i = 0; i < sharedPackagesCount; i++) {
            sharedPackages.add(instr.readUTF());
        }

        // Reader worker implementation classpath
        classPathLength = instr.readInt();
        List<URL> implementationClassPath = new ArrayList<URL>(classPathLength);
        for (int i = 0; i < classPathLength; i++) {
            String url = instr.readUTF();
            implementationClassPath.add(new URL(url));
        }

        // Read serialized worker
        int serializedWorkerLength = instr.readInt();
        byte[] serializedWorker = new byte[serializedWorkerLength];
        instr.readFully(serializedWorker);

        URLClassLoader classLoader = new URLClassLoader(infrastructureClassPath, ClassLoader.getSystemClassLoader().getParent());
        Class<? extends Callable> workerClass = classLoader.loadClass("org.gradle.process.internal.child.SystemApplicationClassLoaderWorker").asSubclass(Callable.class);
        Callable<Void> main = workerClass.getConstructor(Integer.TYPE, Collection.class, Collection.class, byte[].class).newInstance(logLevel, sharedPackages, implementationClassPath, serializedWorker);
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
