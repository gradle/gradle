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

import org.gradle.api.Transformer;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.exceptions.Contextual;
import org.gradle.internal.exceptions.DefaultMultiCauseException;
import org.gradle.internal.exceptions.MultiCauseException;
import org.gradle.internal.io.StreamByteBuffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

class ExceptionPlaceholder implements Serializable {
    private static final long serialVersionUID = 1L;
    private static final Logger LOGGER = LoggerFactory.getLogger(ExceptionPlaceholder.class);
    private final String type;
    private byte[] serializedException;
    private String message;
    private String toString;
    private final boolean contextual;
    private final List<ExceptionPlaceholder> causes;
    private StackTraceElement[] stackTrace;
    private Throwable toStringRuntimeExec;
    private Throwable getMessageExec;

    public ExceptionPlaceholder(final Throwable throwable, Transformer<ExceptionReplacingObjectOutputStream, OutputStream> objectOutputStreamCreator) {
        type = throwable.getClass().getName();
        contextual = throwable.getClass().getAnnotation(Contextual.class) != null;

        try {
            stackTrace = throwable.getStackTrace() == null ? new StackTraceElement[0] : throwable.getStackTrace();
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

        final List<? extends Throwable> causes = extractCauses(throwable);

        StreamByteBuffer buffer = new StreamByteBuffer();
        ExceptionReplacingObjectOutputStream oos = objectOutputStreamCreator.transform(buffer.getOutputStream());
        oos.setObjectTransformer(new Transformer<Object, Object>() {
            boolean seenFirst;

            @Override
            public Object transform(Object obj) {
                if (!seenFirst) {
                    seenFirst = true;
                    return obj;
                }
                // Don't serialize the causes - we'll serialize them separately later
                int causeIndex = causes.indexOf(obj);
                if (causeIndex >= 0) {
                    return new CausePlaceholder(causeIndex);
                }
                return obj;
            }
        });

        try {
            oos.writeObject(throwable);
            oos.close();
            serializedException = buffer.readAsByteArray();
        } catch (Throwable ignored) {
// TODO:ADAM - switch the logging back on.
//                LOGGER.debug("Ignoring failure to serialize throwable.", ignored);
        }

        if (causes.isEmpty()) {
            this.causes = Collections.emptyList();
        } else if (causes.size() == 1) {
            this.causes = Collections.singletonList(new ExceptionPlaceholder(causes.get(0), objectOutputStreamCreator));
        } else {
            List<ExceptionPlaceholder> placeholders = new ArrayList<ExceptionPlaceholder>(causes.size());
            for (Throwable cause : causes) {
                placeholders.add(new ExceptionPlaceholder(cause, objectOutputStreamCreator));
            }
            this.causes = placeholders;
        }
    }

    public Throwable read(Transformer<Class<?>, String> classNameTransformer, Transformer<ExceptionReplacingObjectInputStream, InputStream> objectInputStreamCreator) throws IOException {
        final List<Throwable> causes = recreateCauses(classNameTransformer, objectInputStreamCreator);

        if (serializedException != null) {
            // try to deserialize the original exception
            final ExceptionReplacingObjectInputStream ois = objectInputStreamCreator.transform(new ByteArrayInputStream(serializedException));
            ois.setObjectTransformer(new Transformer<Object, Object>() {
                @Override
                public Object transform(Object obj) {
                    if (obj instanceof CausePlaceholder) {
                        CausePlaceholder placeholder = (CausePlaceholder) obj;
                        return causes.get(placeholder.getCauseIndex());
                    }
                    return obj;
                }
            });

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
            Class<?> clazz = classNameTransformer.transform(type);
            if (clazz != null && causes.size() <= 1) {
                Constructor<?> constructor = clazz.getConstructor(String.class);
                Throwable reconstructed = (Throwable) constructor.newInstance(message);
                if (!causes.isEmpty()) {
                    reconstructed.initCause(causes.get(0));
                }
                reconstructed.setStackTrace(stackTrace);
                return reconstructed;
            }
        } catch (UncheckedException ignore) {
            // Don't log
        } catch (NoSuchMethodException ignored) {
            // Don't log
        } catch (Throwable ignored) {
            LOGGER.debug("Ignoring failure to recreate throwable.", ignored);
        }

        Throwable placeholder;
        if (causes.size() <= 1) {
            if (contextual) {
                placeholder = new ContextualPlaceholderException(type, message, getMessageExec, toString, toStringRuntimeExec, causes.isEmpty() ? null : causes.get(0));
            } else {
                placeholder = new PlaceholderException(type, message, getMessageExec, toString, toStringRuntimeExec, causes.isEmpty() ? null : causes.get(0));
            }
        } else {
            placeholder = new DefaultMultiCauseException(message, causes);
        }
        try {
            placeholder.setStackTrace(stackTrace);
        } catch (NullPointerException e) {
            diagnoseNullPointerException(e);
            throw e;
        }
        return placeholder;
    }

    private void diagnoseNullPointerException(NullPointerException e) {
        // https://github.com/gradle/gradle-private/issues/1368
        StringBuilder sb = new StringBuilder("Malicious stack traces\n");
        for (StackTraceElement element : stackTrace) {
            sb.append(element).append("\n");
        }
        LOGGER.error(sb.toString(), e);
    }

    private List<? extends Throwable> extractCauses(Throwable throwable) {
        if (throwable instanceof MultiCauseException) {
            return ((MultiCauseException) throwable).getCauses();
        } else {
            Throwable causeTmp;
            try {
                causeTmp = throwable.getCause();
            } catch (Throwable ignored) {
                // TODO:ADAM - switch the logging back on.
                //                LOGGER.debug("Ignoring failure to extract throwable cause.", ignored);
                causeTmp = null;
            }
            return causeTmp == null ? Collections.<Throwable>emptyList() : Collections.singletonList(causeTmp);
        }
    }

    private List<Throwable> recreateCauses(Transformer<Class<?>, String> classNameTransformer, Transformer<ExceptionReplacingObjectInputStream, InputStream> objectInputStreamCreator) throws IOException {
        if (causes.isEmpty()) {
            return Collections.emptyList();
        } else if (causes.size() == 1) {
            return Collections.singletonList(causes.get(0).read(classNameTransformer, objectInputStreamCreator));
        }
        List<Throwable> result = new ArrayList<Throwable>();
        for (ExceptionPlaceholder placeholder : causes) {
            result.add(placeholder.read(classNameTransformer, objectInputStreamCreator));
        }
        return result;
    }
}
