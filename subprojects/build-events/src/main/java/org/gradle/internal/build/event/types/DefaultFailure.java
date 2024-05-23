/*
 * Copyright 2019 the original author or authors.
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
package org.gradle.internal.build.event.types;

import org.gradle.tooling.internal.protocol.InternalFailure;

import java.io.PrintWriter;
import java.io.Serializable;
import java.io.StringWriter;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class DefaultFailure implements Serializable, InternalFailure {

    private final String message;
    private final String description;
    private final InternalFailure cause;

    protected DefaultFailure(String message, String description, InternalFailure cause) {
        this.message = message;
        this.description = description;
        this.cause = cause;
    }

    @Override
    public String getMessage() {
        return message;
    }

    @Override
    public String getDescription() {
        return description;
    }

    @Override
    public List<? extends InternalFailure> getCauses() {
        return cause == null ? Collections.emptyList() : Collections.singletonList(cause);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        DefaultFailure that = (DefaultFailure) o;
        return Objects.equals(message, that.message)
            && Objects.equals(description, that.description)
            && Objects.equals(cause, that.cause);
    }

    @Override
    public int hashCode() {
        return Objects.hash(message, description, cause);
    }

    public static InternalFailure fromThrowable(Throwable t) {
        StringWriter out = new StringWriter();
        PrintWriter wrt = new PrintWriter(out);
        t.printStackTrace(wrt);
        Throwable cause = t.getCause();
        InternalFailure causeFailure = cause != null && cause != t ? fromThrowable(cause) : null;
        return new DefaultFailure(t.getMessage(), out.toString(), causeFailure);
    }
}
