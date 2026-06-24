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
import org.gradle.tooling.events.problems.Problem;
import org.gradle.tooling.internal.protocol.FailureDescriptionReconstructor;
import org.jspecify.annotations.Nullable;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class DefaultFailure implements Failure {

    private final String message;
    private final @Nullable String fullDescription;
    private final @Nullable String ownDescription;
    private final List<? extends Failure> causes;
    private final List<Problem> problems;
    private @Nullable String reconstructedDescription;

    public DefaultFailure(String message, String description, List<? extends Failure> causes) {
        this(message, description, causes, Collections.<Problem>emptyList());
    }

    public DefaultFailure(String message, String description, List<? extends Failure> causes, List<Problem> problems) {
        this(message, description, description, causes, problems);
    }

    private DefaultFailure(String message, @Nullable String fullDescription, @Nullable String ownDescription, List<? extends Failure> causes, List<Problem> problems) {
        this.message = message;
        this.fullDescription = fullDescription;
        this.ownDescription = ownDescription;
        this.causes = causes;
        this.problems = problems;
    }

    public static DefaultFailure fromOwnDescription(String message, String ownDescription, List<? extends Failure> causes, List<Problem> problems) {
        return new DefaultFailure(message, null, ownDescription, causes, problems);
    }

    @Override
    public String getMessage() {
        return message;
    }

    @Override
    public String getDescription() {
        if (fullDescription != null) {
            return fullDescription;
        }
        String reconstructed = reconstructedDescription;
        if (reconstructed == null) {
            // Walk the concrete node type rather than the Failure interface: own text is an internal detail used only
            // to reassemble the full description, and is not part of the public Failure API.
            reconstructed = FailureDescriptionReconstructor.reconstruct(this, DefaultFailure::getOwnDescription, DefaultFailure::ownCauses);
            reconstructedDescription = reconstructed;
        }
        return reconstructed;
    }

    private @Nullable String getOwnDescription() {
        return ownDescription;
    }

    private List<DefaultFailure> ownCauses() {
        List<DefaultFailure> result = new ArrayList<>(causes.size());
        for (Failure cause : causes) {
            result.add((DefaultFailure) cause);
        }
        return result;
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
        List<DefaultFailure> causes = cause != null && cause != t
            ? Collections.singletonList(fromThrowable(cause))
            : Collections.emptyList();
        return new DefaultFailure(t.getMessage(), out.toString(), causes);
    }

    @Override
    public String toString() {
        return "DefaultFailure{" +
            "message='" + message + '\'' +
            ", ownDescription='" + ownDescription + '\'' +
            ", causes=" + causes +
            ", problems=" + problems +
            '}';
    }
}
