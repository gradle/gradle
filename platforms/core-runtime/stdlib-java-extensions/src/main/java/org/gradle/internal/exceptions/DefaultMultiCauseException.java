/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.internal.exceptions;

import org.gradle.api.GradleException;
import org.gradle.internal.Factory;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@NullMarked
public class DefaultMultiCauseException extends GradleException implements MultiCauseException, NonGradleCauseExceptionsHolder {
    private final List<Throwable> causes = new CopyOnWriteArrayList<>();
    private final List<String> causeResolutions = new ArrayList<>();
    private transient ThreadLocal<Boolean> hideCause = threadLocal();
    private transient @Nullable Factory<String> messageFactory;
    private @Nullable String message;

    public DefaultMultiCauseException(String message) {
        super(message);
        this.message = message;
    }

    @SuppressWarnings("this-escape")
    public DefaultMultiCauseException(String message, Throwable... causes) {
        super(message);
        this.message = message;
        initCauses(Arrays.asList(causes));
    }

    @SuppressWarnings("this-escape")
    public DefaultMultiCauseException(String message, Iterable<? extends Throwable> causes) {
        super(message);
        this.message = message;
        initCauses(causes);
    }

    @SuppressWarnings("this-escape")
    public DefaultMultiCauseException(String message, Iterable<? extends Throwable> causes, List<String> resolutions) {
        super(message);
        resolutions.forEach(this::addResolution);
        this.message = message;
        initCauses(causes);
    }

    public DefaultMultiCauseException(Factory<String> messageFactory) {
        this.messageFactory = messageFactory;
    }

    @SuppressWarnings("this-escape")
    public DefaultMultiCauseException(Factory<String> messageFactory, Throwable... causes) {
        this(messageFactory);
        initCauses(Arrays.asList(causes));
    }

    @SuppressWarnings("this-escape")
    public DefaultMultiCauseException(Factory<String> messageFactory, Iterable<? extends Throwable> causes) {
        this(messageFactory);
        initCauses(causes);
    }

    private void readObject(ObjectInputStream inputStream) throws IOException, ClassNotFoundException {
        inputStream.defaultReadObject();
        hideCause = threadLocal();
    }

    private void writeObject(java.io.ObjectOutputStream out) throws IOException {
        // Ensure fields are initialized before serialization
        String ignored = getMessage();
        out.defaultWriteObject();
    }

    protected List<String> getDirectResolutions() {
        return super.getResolutions();
    }

    private ThreadLocal<Boolean> threadLocal() {
        return new HideStacktrace();
    }

    private static class HideStacktrace extends ThreadLocal<Boolean> {
        @Override
        protected Boolean initialValue() {
            return false;
        }
    }

    @Override
    public List<? extends Throwable> getCauses() {
        return causes;
    }

    @Override
    public synchronized Throwable initCause(Throwable throwable) {
        initCauses(Collections.singletonList(throwable));
        return this;
    }

    /**
     * Replaces this exception's causes, along with the resolutions contributed by the previous ones.
     * <p>
     * A cause's resolutions are copied once, here, rather than read on every {@link #getResolutions()} call.
     * That is what lets {@link #clearResolutions()} work at all - resolutions re-read from the causes on each
     * call could never be cleared. The trade-off is that a cause is treated as a snapshot: a resolution added
     * to a cause <em>after</em> it has been attached here does not reach this exception. Call this method again
     * to pick up such a late addition, or add the resolution to this exception directly with
     * {@link #addResolution(String)}.
     */
    public void initCauses(Iterable<? extends Throwable> causes) {
        this.causes.clear();
        this.causeResolutions.clear();
        addCauses(causes);
    }

    private void addCauses(Iterable<? extends Throwable> causes) {
        for (Throwable cause : causes) {
            this.causes.add(cause);
            addResolutionsFrom(cause);
        }
    }

    private void addResolutionsFrom(Throwable cause) {
        if (cause instanceof ResolutionProvider) {
            List<String> fromCause = ((ResolutionProvider) cause).getResolutions();
            causeResolutions.addAll(fromCause);
        }
    }

    /**
     * Clears both the resolutions added directly to this exception and those contributed by its causes.
     * <p>
     * Unlike {@link #getResolutions()}, this is deliberately left non-{@code final}. {@code getResolutions()}
     * predates the addition of {@code clearResolutions()}, so making <em>it</em> {@code final} can only break a
     * subclass that genuinely overrode it - which is exactly the hazard that needs closing, since such an override
     * would silently turn {@link #addResolution(String)} and this method into no-ops. {@code clearResolutions()}
     * is a new name, and public API types such as {@code org.gradle.api.artifacts.ResolveException} and
     * {@code org.gradle.api.ProjectConfigurationException} extend this class.
     * Making a new name {@code final} would stop any already-compiled subclass that happens to declare a method
     * with that name from loading at all - a binary break for third parties, with nothing gained in return.
     */
    @Override
    public void clearResolutions() {
        causeResolutions.clear();
        super.clearResolutions();
    }

    /**
     * Returns the resolutions added directly to this exception, followed by those contributed by its causes.
     * <p>
     * This is {@code final} because a subclass that overrode it the old way - computing resolutions from the causes
     * itself - would silently turn {@link #addResolution(String)} and {@link #clearResolutions()} into no-ops.
     * Subclasses that need only the directly-added resolutions can use {@link #getDirectResolutions()}.
     */
    @Override
    public final List<String> getResolutions() {
        List<String> combined = new ArrayList<>(super.getResolutions());
        combined.addAll(causeResolutions);
        return Collections.unmodifiableList(combined);
    }

    @Override
    public synchronized @Nullable Throwable getCause() {
        if (hideCause.get()) {
            return null;
        }
        return causes.isEmpty() ? null : causes.get(0);
    }

    @SuppressWarnings("DefaultCharset")
    @Override
    public void printStackTrace(PrintStream printStream) {
        PrintWriter writer = new PrintWriter(printStream);
        printStackTrace(writer);
        writer.flush();
    }

    @Override
    public void printStackTrace(PrintWriter printWriter) {
        if (causes.isEmpty()) {
            super.printStackTrace(printWriter);
            return;
        }

        hideCause.set(true);
        try {
            super.printStackTrace(printWriter);

            if (causes.size() == 1) {
                printSingleCauseStackTrace(printWriter);
            } else {
                printMultiCauseStackTrace(printWriter);
            }
        } finally {
            hideCause.set(false);
        }
    }

    private void printSingleCauseStackTrace(PrintWriter printWriter) {
        Throwable cause = causes.get(0);
        printWriter.print("Caused by: ");
        cause.printStackTrace(printWriter);
    }

    private void printMultiCauseStackTrace(PrintWriter printWriter) {
        for (int i = 0; i < causes.size(); i++) {
            Throwable cause = causes.get(i);
            printWriter.format("Cause %s: ", i + 1);
            cause.printStackTrace(printWriter);
        }
    }

    @Override
    @Nullable
    public String getMessage() {
        if (messageFactory != null) {
            message = messageFactory.create();
            messageFactory = null;
            return message;
        }
        return message;
    }

    @Override
    public boolean hasCause(Class<?> type) {
        for (Throwable cause : getCauses()) {
            if (cause instanceof NonGradleCauseExceptionsHolder) {
                boolean hasCauseOfType = ((NonGradleCauseExceptionsHolder) cause).hasCause(type);
                if (hasCauseOfType) {
                    return true;
                }
            } else if (type.isInstance(cause)) {
                return true;
            }
        }
        return false;
    }
}
