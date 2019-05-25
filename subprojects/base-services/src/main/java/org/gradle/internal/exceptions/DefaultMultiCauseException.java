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

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class DefaultMultiCauseException extends GradleException implements MultiCauseException {
    private final List<Throwable> causes = new CopyOnWriteArrayList<Throwable>();
    private transient ThreadLocal<Boolean> hideCause = threadLocal();
    private transient Factory<String> messageFactory;
    private String message;

    public DefaultMultiCauseException(String message) {
        super(message);
        this.message = message;
    }

    public DefaultMultiCauseException(String message, Throwable... causes) {
        super(message);
        this.message = message;
        this.causes.addAll(Arrays.asList(causes));
    }

    public DefaultMultiCauseException(String message, Iterable<? extends Throwable> causes) {
        super(message);
        this.message = message;
        initCauses(causes);
    }

    public DefaultMultiCauseException(Factory<String> messageFactory) {
        this.messageFactory = messageFactory;
    }

    public DefaultMultiCauseException(Factory<String> messageFactory, Throwable... causes) {
        this(messageFactory);
        this.causes.addAll(Arrays.asList(causes));
    }

    public DefaultMultiCauseException(Factory<String> messageFactory, Iterable<? extends Throwable> causes) {
        this(messageFactory);
        initCauses(causes);
    }

    private void readObject(ObjectInputStream inputStream) throws IOException, ClassNotFoundException {
        inputStream.defaultReadObject();
        hideCause = threadLocal();
    }

    private void writeObject(java.io.ObjectOutputStream out) throws IOException {
        getMessage();
        out.defaultWriteObject();
    }

    private ThreadLocal<Boolean> threadLocal() {
        return new ThreadLocal<Boolean>() {
            @Override
            protected Boolean initialValue() {
                return false;
            }
        };
    }

    @Override
    public List<? extends Throwable> getCauses() {
        return causes;
    }

    @Override
    public Throwable initCause(Throwable throwable) {
        causes.clear();
        causes.add(throwable);
        return null;
    }

    public void initCauses(Iterable<? extends Throwable> causes) {
        this.causes.clear();
        for (Throwable cause : causes) {
            this.causes.add(cause);
        }
    }

    @Override
    public Throwable getCause() {
        if (hideCause.get()) {
            return null;
        }
        return causes.isEmpty() ? null : causes.get(0);
    }

    @Override
    public void printStackTrace(PrintStream printStream) {
        PrintWriter writer = new PrintWriter(printStream);
        printStackTrace(writer);
        writer.flush();
    }

    @Override
    public void printStackTrace(PrintWriter printWriter) {
        if (causes.size() <= 1) {
            super.printStackTrace(printWriter);
            return;
        }

        hideCause.set(true);
        try {
            super.printStackTrace(printWriter);
            for (int i = 0; i < causes.size(); i++) {
                Throwable cause = causes.get(i);
                printWriter.format("Cause %s: ", i + 1);
                cause.printStackTrace(printWriter);
            }
        } finally {
            hideCause.set(false);
        }
    }

    @Override
    public String getMessage() {
        if (messageFactory != null) {
            message = messageFactory.create();
            messageFactory = null;
            return message;
        }
        return message;
    }
}
