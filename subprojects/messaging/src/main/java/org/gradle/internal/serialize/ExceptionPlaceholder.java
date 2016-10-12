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

import org.gradle.internal.io.StreamByteBuffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.lang.reflect.Constructor;

class ExceptionPlaceholder implements Serializable {
    private static final Logger LOGGER = LoggerFactory.getLogger(ExceptionPlaceholder.class);
    private final String type;
    private byte[] serializedException;
    private String message;
    private String toString;
    private ExceptionPlaceholder cause;
    private StackTraceElement[] stackTrace;
    private Throwable toStringRuntimeExec;
    private Throwable getMessageExec;

    public ExceptionPlaceholder(final Throwable throwable) throws IOException {
        type = throwable.getClass().getName();

        try {
            stackTrace = throwable.getStackTrace();
        } catch (Throwable ignored) {
// TODO:ADAM - switch the logging back on. Need to make sending messages from daemon to client async wrt log event generation
//                LOGGER.debug("Ignoring failure to extract throwable stack trace.", ignored);
            stackTrace = new StackTraceElement[0];
        }

        try {
            message = throwable.getMessage();
        } catch (Throwable failure) {
            getMessageExec = failure;
        }

        try {
            toString = throwable.toString();
        } catch (Throwable failure) {
            toStringRuntimeExec = failure;
        }

        Throwable causeTmp;
        try {
            causeTmp = throwable.getCause();
        } catch (Throwable ignored) {
// TODO:ADAM - switch the logging back on.
//                LOGGER.debug("Ignoring failure to extract throwable cause.", ignored);
            causeTmp = null;
        }
        final Throwable causeFinal = causeTmp;

        StreamByteBuffer buffer = new StreamByteBuffer();
        ObjectOutputStream oos = new ExceptionReplacingObjectOutputStream(buffer.getOutputStream()) {
            boolean seenFirst;

            @Override
            protected Object replaceObject(Object obj) throws IOException {
                if (!seenFirst) {
                    seenFirst = true;
                    return obj;
                }
                // Don't serialize the cause - we'll serialize it separately later
                if (obj == causeFinal) {
                    return new CausePlaceholder();
                }
                return super.replaceObject(obj);
            }
        };

        try {
            oos.writeObject(throwable);
            oos.close();
            serializedException = buffer.readAsByteArray();
        } catch (Throwable ignored) {
// TODO:ADAM - switch the logging back on.
//                LOGGER.debug("Ignoring failure to serialize throwable.", ignored);
        }

        if (causeFinal != null) {
            cause = new ExceptionPlaceholder(causeFinal);
        }
    }

    public Throwable read(ClassLoader classLoader) throws IOException {
        final Throwable causeThrowable = getCause(classLoader);

        if (serializedException != null) {
            // try to deserialize the original exception
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
            } catch (ClassNotFoundException ignored) {
                // Don't log
            } catch (Throwable failure) {
                LOGGER.debug("Ignoring failure to de-serialize throwable.", failure);
            }
        }

        try {
            // try to reconstruct the exception
            Constructor<?> constructor = classLoader.loadClass(type).getConstructor(String.class);
            Throwable reconstructed = (Throwable) constructor.newInstance(message);
            reconstructed.initCause(causeThrowable);
            reconstructed.setStackTrace(stackTrace);
            return reconstructed;
        } catch (ClassNotFoundException ignored) {
            // Don't log
        } catch (NoSuchMethodException ignored) {
            // Don't log
        } catch (Throwable ignored) {
            LOGGER.debug("Ignoring failure to recreate throwable.", ignored);
        }

        Throwable placeholder = new PlaceholderException(type, message, getMessageExec, toString, toStringRuntimeExec, causeThrowable);
        placeholder.setStackTrace(stackTrace);
        return placeholder;
    }

    private Throwable getCause(ClassLoader classLoader) throws IOException {
        return cause != null ? cause.read(classLoader) : null;
    }
}
