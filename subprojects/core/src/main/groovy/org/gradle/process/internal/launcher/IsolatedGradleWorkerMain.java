/*
 * Copyright 2014 the original author or authors.
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

import org.gradle.api.Action;
import org.gradle.process.internal.child.EncodedStream;
import org.gradle.process.internal.child.IsolatedApplicationClassLoaderWorker;
import org.gradle.process.internal.child.WorkerContext;

import java.io.DataInputStream;
import java.io.ObjectInputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;

/**
 * The main entry point for a worker process, using isolated ClassLoader strategy. Reads the application classpath and a serialized worker action from stdin, and delegates
 * to {@link org.gradle.process.internal.child.IsolatedApplicationClassLoaderWorker} to create the appropriate ClassLoader and run the action.
 */
public class IsolatedGradleWorkerMain {
    public void run() throws Exception {
        // Read the main action from stdin and execute it
        DataInputStream instr = new DataInputStream(new EncodedStream.EncodedInput(System.in));
        int applicationClassPathLength = instr.readInt();
        Collection<URI> classpath = new ArrayList<URI>();
        for (int i = 0; i < applicationClassPathLength; i++) {
            String uri = instr.readUTF();
            classpath.add(new URI(uri));
        }
        ObjectInputStream objectInputStream = new ObjectInputStream(instr);
        Action<WorkerContext> worker = (Action<WorkerContext>) objectInputStream.readObject();

        new IsolatedApplicationClassLoaderWorker(classpath, worker).call();
    }

    public static void main(String[] args) {
        try {
            new IsolatedGradleWorkerMain().run();
            System.exit(0);
        } catch (Throwable throwable) {
            throwable.printStackTrace(System.err);
            System.exit(1);
        }
    }
}
