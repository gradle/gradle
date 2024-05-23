/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.tooling.internal.consumer;

import org.gradle.tooling.Failure;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class DefaultFailure implements Failure {

    private final String message;
    private final String description;
    private final List<? extends Failure> causes;

    public DefaultFailure(String message, String description, List<? extends Failure> causes) {
        this.message = message;
        this.description = description;
        this.causes = causes;
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
            && Objects.equals(causes, that.causes);
    }

    @Override
    public int hashCode() {
        return Objects.hash(message, description, causes);
    }

    @Override
    public List<? extends Failure> getCauses() {
        return causes;
    }

    public static DefaultFailure fromThrowable(Throwable t) {
        StringWriter out = new StringWriter();
        PrintWriter wrt = new PrintWriter(out);
        t.printStackTrace(wrt);
        Throwable cause = t.getCause();
        DefaultFailure causeFailure = cause != null && cause != t ? fromThrowable(cause) : null;
        return new DefaultFailure(t.getMessage(), out.toString(), Collections.singletonList(causeFailure));
    }
}
