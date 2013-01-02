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

import org.gradle.internal.UncheckedException;
import org.gradle.internal.io.ClassLoaderObjectInputStream;

import java.io.*;
import java.lang.reflect.Constructor;

public abstract class Message implements Serializable {
    public static void send(Object message, OutputStream outputSteam) throws IOException {
        ObjectOutputStream oos = new ExceptionReplacingObjectOutputStream(outputSteam);
        try {
            oos.writeObject(message);
        } finally {
            oos.flush();
        }
    }

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

            type = throwable.getClass().getName();
            message = throwable.getMessage();
            try {
                toString = throwable.toString();
            } catch (RuntimeException toStringRE) {
                toString = null;
                toStringRuntimeExec = toStringRE;
            }
            if (throwable.getCause() != null) {
                cause = new ExceptionPlaceholder(throwable.getCause());
            }
            stackTrace = throwable.getStackTrace();
        }

        public Throwable read(ClassLoader classLoader) throws IOException {
            final Throwable causeThrowable = getCause(classLoader);
            Throwable throwable = null;
            if (serializedException != null) {
                try {
                    final ExceptionReplacingObjectInputStream ois = new ExceptionReplacingObjectInputStream(new ByteArrayInputStream(serializedException), classLoader) {
                        @Override
                        protected Object resolveObject(Object obj) throws IOException {
                            if (obj instanceof CausePlaceholder) {
                                return causeThrowable;
                            }
                            return super.resolveObject(obj);
                        }
                    };
                    throwable = (Throwable) ois.readObject();
                } catch (ClassNotFoundException e) {
                    // Ignore
                } catch (InvalidClassException e) {
                    try {
                        Constructor<?> constructor = classLoader.loadClass(type).getConstructor(String.class);
                        throwable = (Throwable) constructor.newInstance(message);
                        throwable.initCause(causeThrowable);
                        throwable.setStackTrace(stackTrace);
                    } catch (ClassNotFoundException e1) {
                        // Ignore
                    } catch (NoSuchMethodException e1) {
                        // Ignore
                    } catch (Throwable t) {
                        throw UncheckedException.throwAsUncheckedException(t);
                    }
                }
            }

            if (throwable == null) {
                throwable = new PlaceholderException(type, message, toString, toStringRuntimeExec, causeThrowable);
                throwable.setStackTrace(stackTrace);
            }

            return throwable;
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
