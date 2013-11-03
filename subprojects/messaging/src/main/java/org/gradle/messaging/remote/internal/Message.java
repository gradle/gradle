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

package org.gradle.messaging.remote.internal;

import org.gradle.internal.io.ClassLoaderObjectInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.lang.reflect.Constructor;

public abstract class Message implements Serializable {

    private static final Logger LOGGER = LoggerFactory.getLogger(Message.class);

    /**
     * Serialize the <code>message</code> onto the provided <code>outputStream</code>,
     * replacing all {@link Throwable}s in the object graph with a placeholder object
     * that can be read back by {@link #receive(java.io.InputStream, ClassLoader)}.
     *
     * @param message object to serialize
     * @param outputSteam stream to serialize onto
     * @throws IOException
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
     * Read back an object from the provided stream that has been serialized by a call to {@link #send(Object, java.io.OutputStream)}.
     * Any {@link Throwable} that cannot be de-serialized (for whatever reason) will be replaced by a {@link PlaceholderException}.
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

    private static class ExceptionPlaceholder implements Serializable {
        private byte[] serializedException;
        private String type;
        private String message;
        private String toString;
        private ExceptionPlaceholder cause;
        private StackTraceElement[] stackTrace;
        private RuntimeException toStringRuntimeExec;

        public ExceptionPlaceholder(final Throwable throwable) throws IOException {
            try {
                tryPopulateFrom(throwable);
            } catch (Throwable ignored) {
                // it is inherently unsafe to inspect user-throw exceptions (e.g. from tests)
                LOGGER.info("Ignoring error serializing exception", ignored);
            }
        }

        private void tryPopulateFrom(final Throwable throwable) throws Throwable {
            type = throwable.getClass().getName();
            stackTrace = throwable.getStackTrace();
            message = throwable.getMessage();
            try {
                toString = throwable.toString();
            } catch (RuntimeException toStringRE) {
                toString = null;
                toStringRuntimeExec = toStringRE;
            }

            ByteArrayOutputStream outstr = new ByteArrayOutputStream();
            ObjectOutputStream oos = new ExceptionReplacingObjectOutputStream(outstr) {
                @Override
                protected Object replaceObject(Object obj) throws IOException {
                    if (obj == throwable) {
                        return throwable;
                    }
                    // Don't serialize the cause - we'll serialize it separately later
                    if (obj == throwable.getCause()) {
                        return new CausePlaceholder();
                    }
                    return super.replaceObject(obj);
                }
            };
            try {
                oos.writeObject(throwable);
                oos.close();
                serializedException = outstr.toByteArray();
            } catch (NotSerializableException e) {
                // Ignore
            }

            if (throwable.getCause() != null) {
                cause = new ExceptionPlaceholder(throwable.getCause());
            }
        }

        public Throwable read(ClassLoader classLoader) throws IOException {
            final Throwable causeThrowable = getCause(classLoader);

            try {
                return tryDeserialize(classLoader, causeThrowable);
            } catch (Throwable ignored) {
                // it is inherently unsafe to de-serialize user thrown exceptions (e.g. from tests)
                LOGGER.info("Ignoring error de-serializing throwable", ignored);
                Throwable placeholder = new PlaceholderException(type, message, toString, toStringRuntimeExec, causeThrowable);
                placeholder.setStackTrace(stackTrace);
                return placeholder;
            }
        }

        private Throwable tryDeserialize(final ClassLoader classLoader, final Throwable causeThrowable) throws Exception {
            if (serializedException != null) {
                final ExceptionReplacingObjectInputStream ois = new ExceptionReplacingObjectInputStream(new ByteArrayInputStream(serializedException), classLoader) {
                    @Override
                    protected Object resolveObject(Object obj) throws IOException {
                        if (obj instanceof CausePlaceholder) {
                            return causeThrowable;
                        }
                        return super.resolveObject(obj);
                    }
                };
                try {
                    return (Throwable) ois.readObject();
                } catch (Exception e) {
                    LOGGER.info("Ignoring error de-serializing exception", e);
                }
            }
            // try to reconstruct the exception - also potentially unsafe
            Constructor<?> constructor = classLoader.loadClass(type).getConstructor(String.class);
            Throwable reconstructed = (Throwable) constructor.newInstance(message);
            reconstructed.initCause(causeThrowable);
            reconstructed.setStackTrace(stackTrace);
            return reconstructed;
        }

        private Throwable getCause(ClassLoader classLoader) throws IOException {
            return cause != null ? cause.read(classLoader) : null;
        }
    }

    private static class CausePlaceholder implements Serializable {
    }

    private static class TopLevelExceptionPlaceholder extends ExceptionPlaceholder {
        private TopLevelExceptionPlaceholder(Throwable throwable) throws IOException {
            super(throwable);
        }
    }

    private static class ExceptionReplacingObjectOutputStream extends ObjectOutputStream {
        public ExceptionReplacingObjectOutputStream(OutputStream outputSteam) throws IOException {
            super(outputSteam);
            enableReplaceObject(true);
        }

        @Override
        protected Object replaceObject(Object obj) throws IOException {
            if (obj instanceof Throwable) {
                return new TopLevelExceptionPlaceholder((Throwable) obj);
            }
            return obj;
        }
    }

    private static class ExceptionReplacingObjectInputStream extends ClassLoaderObjectInputStream {
        public ExceptionReplacingObjectInputStream(InputStream inputSteam, ClassLoader classLoader) throws IOException {
            super(inputSteam, classLoader);
            enableResolveObject(true);
        }

        @Override
        protected Object resolveObject(Object obj) throws IOException {
            if (obj instanceof TopLevelExceptionPlaceholder) {
                return ((ExceptionPlaceholder) obj).read(getClassLoader());
            }
            return obj;
        }
    }
}
