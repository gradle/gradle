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

import java.io.ObjectInputStream;
import java.util.concurrent.Callable;

/**
 * The main entry point for a worker process. Reads a serialized Callable from stdin, and executes it.
 */
public class GradleWorkerMain {
    public void run() throws Exception {
        // Read the main action from stdin and execute it
        ObjectInputStream instr = new ObjectInputStream(new EncodedStream.EncodedInput(System.in));
        Callable<?> main = (Callable<?>) instr.readObject();
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
