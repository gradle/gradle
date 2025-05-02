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

import org.gradle.api.JavaVersion;
import org.gradle.internal.Cast;
import org.gradle.internal.InternalTransformer;
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
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

class ExceptionPlaceholder implements Serializable {
    @SuppressWarnings("DoubleBraceInitialization")
    // TODO Use Guava's immutable collections here
    private static final Set<String> CANDIDATE_GET_CAUSES = Collections.unmodifiableSet(
        new HashSet<String>() {{
            add("getCauses");
            add("getFailures");
        }}
    );
    private static final long serialVersionUID = 1L;
    private static final Logger LOGGER = LoggerFactory.getLogger(ExceptionPlaceholder.class);
    private final String type;
    private byte[] serializedException;
    private String message;
    private String toString;
    private final boolean contextual;
    private final boolean assertionError;
    private final List<ExceptionPlaceholder> causes;
    private final List<ExceptionPlaceholder> suppressed;
    private List<StackTraceElementPlaceholder> stackTrace;
    private Throwable toStringRuntimeExec;
    private Throwable getMessageExec;

    public ExceptionPlaceholder(Throwable original, InternalTransformer<ExceptionReplacingObjectOutputStream, OutputStream> objectOutputStreamCreator, Set<Throwable> dejaVu) {
        boolean hasCycle = !dejaVu.add(original);
        Throwable throwable = original;
        type = throwable.getClass().getName();
        contextual = throwable.getClass().getAnnotation(Contextual.class) != null;
        assertionError = throwable instanceof AssertionError;
        try {
            stackTrace = throwable.getStackTrace() == null ? Collections.<StackTraceElementPlaceholder>emptyList() : convertStackTrace(throwable.getStackTrace());
        } catch (Throwable ignored) {
// TODO:ADAM - switch the logging back on. Need to make sending messages from daemon to client async wrt log event generation
//                LOGGER.debug("Ignoring failure to extract throwable stack trace.", ignored);
            stackTrace = Collections.emptyList();
        }

        try {
            message = throwable.getMessage();
        } catch (Throwable failure) {
            getMessageExec = failure;
        }

        if (isJava14()) {
            throwable = Java14NullPointerExceptionUsefulMessageSupport.maybeReplaceUsefulNullPointerMessage(throwable);
        }

        try {
            toString = throwable.toString();
        } catch (Throwable failure) {
            toStringRuntimeExec = failure;
        }

        final List<? extends Throwable> causes;
        final List<? extends Throwable> suppressed;
        if (hasCycle) {
            // Ignore causes and suppressed in case of cycle
            causes = Collections.emptyList();
            suppressed = Collections.emptyList();
        } else {
            causes = extractCauses(throwable);
            suppressed = extractSuppressed(throwable);
        }

        StreamByteBuffer buffer = new StreamByteBuffer();
        ExceptionReplacingObjectOutputStream oos = objectOutputStreamCreator.transform(buffer.getOutputStream());
        oos.setObjectTransformer(new InternalTransformer<Object, Object>() {
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
                    return new NestedExceptionPlaceholder(NestedExceptionPlaceholder.Kind.cause, causeIndex);
                }
                int suppressedIndex = suppressed.indexOf(obj);
                if (suppressedIndex >= 0) {
                    return new NestedExceptionPlaceholder(NestedExceptionPlaceholder.Kind.suppressed, suppressedIndex);
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

        this.causes = convertToExceptionPlaceholderList(causes, objectOutputStreamCreator, dejaVu);
        this.suppressed = convertToExceptionPlaceholderList(suppressed, objectOutputStreamCreator, dejaVu);

    }

    private List<StackTraceElementPlaceholder> convertStackTrace(StackTraceElement[] stackTrace) {
        List<StackTraceElementPlaceholder> placeholders = new ArrayList<StackTraceElementPlaceholder>(stackTrace.length);
        for (StackTraceElement stackTraceElement : stackTrace) {
            placeholders.add(new StackTraceElementPlaceholder(stackTraceElement));
        }
        return placeholders;
    }

    private StackTraceElement[] convertStackTrace(List<StackTraceElementPlaceholder> placeholders) {
        StackTraceElement[] stackTrace = new StackTraceElement[placeholders.size()];
        for (int i = 0; i < placeholders.size(); i++) {
            stackTrace[i] = placeholders.get(i).toStackTraceElement();
        }
        return stackTrace;
    }

    @SuppressWarnings("Since15")
    private static List<? extends Throwable> extractSuppressed(Throwable throwable) {
        if (isJava7()) {
            return Arrays.asList(throwable.getSuppressed());
        }
        return Collections.emptyList();
    }

    @SuppressWarnings("MixedMutabilityReturnType")
    // TODO Use only immutable collections
    private static List<ExceptionPlaceholder> convertToExceptionPlaceholderList(List<? extends Throwable> throwables, InternalTransformer<ExceptionReplacingObjectOutputStream, OutputStream> objectOutputStreamCreator, Set<Throwable> dejaVu) {
        if (throwables.isEmpty()) {
            return Collections.emptyList();
        } else if (throwables.size() == 1) {
            return Collections.singletonList(new ExceptionPlaceholder(throwables.get(0), objectOutputStreamCreator, dejaVu));
        } else {
            List<ExceptionPlaceholder> placeholders = new ArrayList<ExceptionPlaceholder>(throwables.size());
            for (Throwable cause : throwables) {
                placeholders.add(new ExceptionPlaceholder(cause, objectOutputStreamCreator, dejaVu));
            }
            return placeholders;
        }
    }

    private static boolean isJava7() {
        return JavaVersion.current().isJava7Compatible();
    }

    private static boolean isJava14() {
        return JavaVersion.current().isCompatibleWith(JavaVersion.VERSION_14);
    }

    public Throwable read(InternalTransformer<Class<?>, String> classNameTransformer, InternalTransformer<ExceptionReplacingObjectInputStream, InputStream> objectInputStreamCreator) throws IOException {
        final List<Throwable> causes = recreateExceptions(this.causes, classNameTransformer, objectInputStreamCreator);
        final List<Throwable> suppressed = recreateExceptions(this.suppressed, classNameTransformer, objectInputStreamCreator);

        if (serializedException != null) {
            // try to deserialize the original exception
            final ExceptionReplacingObjectInputStream ois = objectInputStreamCreator.transform(new ByteArrayInputStream(serializedException));
            ois.setObjectTransformer(new InternalTransformer<Object, Object>() {
                @Override
                public Object transform(Object obj) {
                    if (obj instanceof NestedExceptionPlaceholder) {
                        NestedExceptionPlaceholder placeholder = (NestedExceptionPlaceholder) obj;
                        int index = placeholder.getIndex();
                        switch (placeholder.getKind()) {
                            case cause:
                                return causes.get(index);
                            case suppressed:
                                return suppressed.get(index);
                        }
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
                reconstructed.setStackTrace(convertStackTrace(stackTrace));
                registerSuppressedExceptions(suppressed, reconstructed);
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
                // there are no @Contextual assertion errors in Gradle so we're safe to use this type only
                placeholder = new ContextualPlaceholderException(type, message, getMessageExec, toString, toStringRuntimeExec, causes.isEmpty() ? null : causes.get(0));
            } else {
                if (assertionError) {
                    placeholder = new PlaceholderAssertionError(type, message, getMessageExec, toString, toStringRuntimeExec, causes.isEmpty() ? null : causes.get(0));
                } else {
                    placeholder = new PlaceholderException(type, message, getMessageExec, toString, toStringRuntimeExec, causes.isEmpty() ? null : causes.get(0));
                }
            }
        } else {
            placeholder = new DefaultMultiCauseException(message, causes);
        }
        placeholder.setStackTrace(convertStackTrace(stackTrace));
        registerSuppressedExceptions(suppressed, placeholder);
        return placeholder;
    }

    private static void registerSuppressedExceptions(List<Throwable> suppressed, Throwable reconstructed) {
        if (!suppressed.isEmpty()) {
            for (Throwable throwable : suppressed) {
                //noinspection Since15
                reconstructed.addSuppressed(throwable);
            }
        }
    }

    private static List<? extends Throwable> extractCauses(Throwable throwable) {
        if (throwable instanceof MultiCauseException) {
            return ((MultiCauseException) throwable).getCauses();
        } else {
            List<? extends Throwable> causes = tryExtractMultiCauses(throwable);
            if (causes != null) {
                return causes;
            }
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

    private static Method findCandidateGetCausesMethod(Throwable throwable) {
        Method[] declaredMethods = throwable.getClass().getDeclaredMethods();
        for (Method method : declaredMethods) {
            if (CANDIDATE_GET_CAUSES.contains(method.getName())) {
                Class<?> returnType = method.getReturnType();
                if (Collection.class.isAssignableFrom(returnType)) {
                    return method;
                }
            }
        }
        return null;
    }

    /**
     * Does best effort to find a method which potentially returns multiple causes
     * for an exception. This is for classes of external projects which actually do
     * something similar to what we do in Gradle with {@link DefaultMultiCauseException}.
     * It is, in particular, the case for opentest4j.
     */
    private static List<? extends Throwable> tryExtractMultiCauses(Throwable throwable) {
        Method causesMethod = findCandidateGetCausesMethod(throwable);
        if (causesMethod != null) {
            Collection<?> causes;
            try {
                causes = Cast.uncheckedCast(causesMethod.invoke(throwable));
            } catch (IllegalAccessException e) {
                return null;
            } catch (InvocationTargetException e) {
                return null;
            }
            if (causes == null) {
                return null;
            }
            for (Object cause : causes) {
                if (!(cause instanceof Throwable)) {
                    return null;
                }
            }
            List<Throwable> result = new ArrayList<Throwable>(causes.size());
            for (Object cause : causes) {
                result.add(Cast.<Throwable>uncheckedCast(cause));
            }
            return result;
        }
        return null;
    }

    @SuppressWarnings("MixedMutabilityReturnType")
    // TODO Use only immutable collections
    private static List<Throwable> recreateExceptions(List<ExceptionPlaceholder> exceptions, InternalTransformer<Class<?>, String> classNameTransformer, InternalTransformer<ExceptionReplacingObjectInputStream, InputStream> objectInputStreamCreator) throws IOException {
        if (exceptions.isEmpty()) {
            return Collections.emptyList();
        } else if (exceptions.size() == 1) {
            return Collections.singletonList(exceptions.get(0).read(classNameTransformer, objectInputStreamCreator));
        }
        List<Throwable> result = new ArrayList<Throwable>();
        for (ExceptionPlaceholder placeholder : exceptions) {
            result.add(placeholder.read(classNameTransformer, objectInputStreamCreator));
        }
        return result;
    }

    /**
     * A support utility which will replace the message of NullPointerException
     * thrown in Java 14+ with the "useful" one when it's not using a custom
     * message.
     *
     * We have to do this because Java 14 will not serialize the required context
     * and therefore when the exception is sent back to the daemon, it loses
     * information required to create a "useful message".
     */
    private static class Java14NullPointerExceptionUsefulMessageSupport {

        private static Throwable maybeReplaceUsefulNullPointerMessage(Throwable throwable) {
            if (throwable instanceof NullPointerException) {
                StackTraceElement[] stackTrace = throwable.getStackTrace();
                try {
                    throwable = new NullPointerException(throwable.getMessage());
                } catch (Exception e) {
                    // if calling `getMessage()` fails for whatever reason, just ignore
                    // the replacement
                    return throwable;
                }
                throwable.setStackTrace(stackTrace);
            }
            return throwable;
        }
    }
}
