/*
 * Copyright 2009 the original author or authors.
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
package org.gradle.listener.dispatch;

import org.gradle.util.ReflectionUtil;

import java.io.*;

public abstract class Message implements Serializable {
    public void send(OutputStream outputSteam) throws IOException {
        ObjectOutputStream oos = new ExceptionReplacingObjectOutputStream(outputSteam);
        try {
            oos.writeObject(this);
        } finally {
            oos.flush();
        }
    }

    public static Message receive(InputStream inputSteam, ClassLoader classLoader)
            throws IOException, ClassNotFoundException {
        ObjectInputStream ois = new ExceptionReplacingObjectInputStream(inputSteam, classLoader);
        return (Message) ois.readObject();
    }

    private static class ExceptionPlaceholder implements Serializable {
        private byte[] serializedException;
        private String type;
        private String message;
        private ExceptionPlaceholder cause;
        private StackTraceElement[] stackTrace;

        public ExceptionPlaceholder(Throwable throwable) throws IOException {
            ByteArrayOutputStream outstr = new ByteArrayOutputStream();
            ObjectOutputStream oos = new ObjectOutputStream(outstr);
            try {
                oos.writeObject(throwable);
                oos.close();
                serializedException = outstr.toByteArray();
            } catch (NotSerializableException e) {
                // Ignore
            }

            type = throwable.getClass().getName();
            message = throwable.getMessage();
            if (throwable.getCause() != null) {
                cause = new ExceptionPlaceholder(throwable.getCause());
            }
            stackTrace = throwable.getStackTrace();
        }

        public Throwable read(ClassLoader classLoader) throws IOException {
            if (serializedException != null) {
                try {
                    return (Throwable) new ClassLoaderObjectInputStream(new ByteArrayInputStream(serializedException),
                            classLoader).readObject();
                } catch (ClassNotFoundException e) {
                    // Ignore
                } catch (InvalidClassException e) {
                    try {
                        Throwable throwable = ReflectionUtil.newInstance(classLoader.loadClass(type), new Object[]{message});
                        throwable.initCause(getCause(classLoader));
                        throwable.setStackTrace(stackTrace);
                        return throwable;
                    } catch (ClassNotFoundException e1) {
                        // Ignore
                    }
                }
            }

            PlaceholderException exception = new PlaceholderException(String.format("%s: %s", type, message), getCause(classLoader));
            exception.setStackTrace(stackTrace);
            return exception;
        }

        private Throwable getCause(ClassLoader classLoader) throws IOException {
            return cause != null ? cause.read(classLoader) : null;
        }
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

    private static class ClassLoaderObjectInputStream extends ObjectInputStream {
        protected final ClassLoader loader;

        private ClassLoaderObjectInputStream(InputStream in, ClassLoader loader) throws IOException {
            super(in);
            this.loader = loader;
        }

        @Override
        protected Class<?> resolveClass(ObjectStreamClass desc) throws IOException, ClassNotFoundException {
            try {
                return loader.loadClass(desc.getName());
            } catch (ClassNotFoundException e) {
                return super.resolveClass(desc);
            }
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
                return ((ExceptionPlaceholder) obj).read(loader);
            }
            return obj;
        }
    }
}
