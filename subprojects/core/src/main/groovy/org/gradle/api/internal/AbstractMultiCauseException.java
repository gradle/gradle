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
package org.gradle.api.internal;

import org.gradle.api.GradleException;
import org.gradle.util.GUtil;

import java.io.PrintStream;
import java.io.PrintWriter;
import java.util.List;

public class AbstractMultiCauseException extends GradleException implements MultiCauseException {
    private List<Throwable> causes;
    private final ThreadLocal<Boolean> hideCause = new ThreadLocal<Boolean>() {
        @Override
        protected Boolean initialValue() {
            return false;
        }
    };

    public AbstractMultiCauseException(String message, Iterable<? extends Throwable> causes) {
        super(message);
        this.causes = GUtil.addLists(causes);
    }

    public List<? extends Throwable> getCauses() {
        return causes;
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
}
