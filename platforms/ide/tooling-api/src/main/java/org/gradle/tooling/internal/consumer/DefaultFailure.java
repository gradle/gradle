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
import org.gradle.tooling.JvmFailure;
import org.gradle.tooling.events.problems.Problem;

import javax.annotation.Nullable;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Collections;
import java.util.List;

public class DefaultFailure implements JvmFailure {
    private final @Nullable Class<? extends Throwable> exceptionType;
    private final String message;
    private final String description;
    private final List<? extends Failure> causes;
    private final List<Problem> problems;

    public DefaultFailure(@Nullable Class<? extends Throwable> exceptionType, String message, String description, List<? extends Failure> causes) {
        this(exceptionType, message, description, causes, Collections.<Problem>emptyList());
    }

    public DefaultFailure(@Nullable Class<? extends Throwable> exceptionType, String message, String description, List<? extends Failure> causes, List<Problem> problems) {
        this.exceptionType = exceptionType;
        this.message = message;
        this.description = description;
        this.causes = causes;
        this.problems = problems;
    }

    @Override
    @Nullable
    public Class<? extends Throwable> getExceptionType() {
        return exceptionType;
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
    public List<? extends Failure> getCauses() {
        return causes;
    }

    @Override
    public List<Problem> getProblems() {
        return problems;
    }

    public static DefaultFailure fromThrowable(Throwable t) {
        StringWriter out = new StringWriter();
        PrintWriter wrt = new PrintWriter(out);
        t.printStackTrace(wrt);
        Throwable cause = t.getCause();
        DefaultFailure causeFailure = cause != null && cause != t ? fromThrowable(cause) : null;
        return new DefaultFailure(t.getClass(), t.getMessage(), out.toString(), Collections.singletonList(causeFailure));
    }
}
