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

import java.io.*;
import java.lang.reflect.Method;

public class Event implements Serializable {
    private transient Method method;
    private String methodName;
    private Class[] parameters;
    private Object[] arguments;

    public Event(Method method, Object[] args) {
        this.method = method;
        methodName = method.getName();
        parameters = method.getParameterTypes();
        arguments = args;
    }

    public String getMethodName() {
        return methodName;
    }

    public Class[] getParameters() {
        return parameters;
    }

    public Object[] getArguments() {
        return arguments;
    }

    public Method getMethod(Class<?> type) throws NoSuchMethodException {
        if (method != null) {
            return method;
        }
        return type.getMethod(methodName, parameters);
    }

    public void send(OutputStream outputSteam) throws IOException {
        ObjectOutputStream oos = new ExceptionReplacingObjectOutputStream(outputSteam);
        try {
            oos.writeObject(this);
        } finally {
            oos.flush();
        }
    }

    public static Event receive(InputStream inputSteam) throws IOException, ClassNotFoundException {
        ObjectInputStream ois = new ExceptionReplacingObjectInputStream(inputSteam);
        return (Event) ois.readObject();
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

        public Throwable read() throws IOException {
            if (serializedException != null) {
                try {
                    return (Throwable) new ObjectInputStream(new ByteArrayInputStream(serializedException))
                            .readObject();
                } catch (ClassNotFoundException e) {
                    // Ignore
                }
            }

            RuntimeException exception = new RuntimeException(String.format("%s: %s", type, message),
                    cause != null ? cause.read() : null);
            exception.setStackTrace(stackTrace);
            return exception;
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

    private static class ExceptionReplacingObjectInputStream extends ObjectInputStream {
        public ExceptionReplacingObjectInputStream(InputStream inputSteam) throws IOException {
            super(inputSteam);
            enableResolveObject(true);
        }

        @Override
        protected Object resolveObject(Object obj) throws IOException {
            if (obj instanceof TopLevelExceptionPlaceholder) {
                return ((ExceptionPlaceholder) obj).read();
            }
            return obj;
        }
    }
}

