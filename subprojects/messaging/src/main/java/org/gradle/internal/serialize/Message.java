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

package org.gradle.internal.serialize;

import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;

public abstract class Message {
    /**
     * Serialize the <code>message</code> onto the provided <code>outputStream</code>, replacing all {@link Throwable}s in the object graph with a placeholder object that can be read back by {@link
     * #receive(java.io.InputStream, ClassLoader)}.
     *
     * @param message object to serialize
     * @param outputSteam stream to serialize onto
     */
    public static void send(Object message, OutputStream outputSteam) throws IOException {
        ObjectOutputStream oos = new ExceptionReplacingObjectOutputStream(outputSteam);
        try {
            oos.writeObject(message);
        } finally {
            oos.flush();
        }
    }

    /**
     * Read back an object from the provided stream that has been serialized by a call to {@link #send(Object, java.io.OutputStream)}. Any {@link Throwable} that cannot be de-serialized (for whatever
     * reason) will be replaced by a {@link PlaceholderException}.
     *
     * @param inputSteam stream to read the object from
     * @param classLoader loader used to load exception classes
     * @return the de-serialized object
     */
    public static Object receive(InputStream inputSteam, ClassLoader classLoader)
            throws IOException, ClassNotFoundException {
        ObjectInputStream ois = new ExceptionReplacingObjectInputStream(inputSteam, classLoader);
        return ois.readObject();
    }

}
