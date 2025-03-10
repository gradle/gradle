/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.internal.buildevents;

import org.gradle.internal.exceptions.Contextual;
import org.gradle.internal.exceptions.LocationAwareException;
import org.gradle.internal.exceptions.MultiCauseException;
import org.gradle.internal.problems.failure.Failure;
import org.gradle.util.internal.TreeVisitor;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class ContextAwareExceptionHandler {

    static void accept(Failure contextAwareException, ExceptionContextVisitor visitor) {
        List<Failure> causes = contextAwareException.getCauses();
        if (!causes.isEmpty()) {
            for (Failure cause : causes) {
                visitor.visitCause(cause);
                visitCauses(cause, visitor);

            }
        }
        visitor.endVisiting();
        if (contextAwareException.getOriginal() instanceof LocationAwareException) {
            String location = ((LocationAwareException) contextAwareException.getOriginal()).getLocation();
            if (location != null) {
                visitor.visitLocation(location);
            }
        }
    }

    /**
     * Returns the reportable causes for this failure.
     *
     * @return The causes. Never returns null, returns an empty list if this exception has no reportable causes.
     */
    public static List<Failure> getReportableCauses(Failure failure) {
        final List<Failure> causes = new ArrayList<>();
        for (Failure cause : failure.getCauses()) {
            visitCauses(cause, new TreeVisitor<Failure>() {
                @Override
                public void node(Failure node) {
                    causes.add(node);
                }
            });
        }

        return causes;
    }

    private static void visitCauses(Failure t, TreeVisitor<? super Failure> visitor) {
        List<Failure> causes = t.getCauses();
        if (!causes.isEmpty()) {
            visitor.startChildren();
            for (Failure cause : causes) {
                visitContextual(cause, visitor);
            }
            visitor.endChildren();
        }
    }

    private static void visitContextual(Failure t, TreeVisitor<? super Failure> visitor) {
        Failure next = findNearestContextual(t);
        if (next != null) {
            // Show any contextual cause recursively
            visitor.node(next);
            visitCauses(next, visitor);
        } else {
            // Show the direct cause of the last contextual cause only
            visitor.node(t);
        }
    }

    @Nullable
    private static Failure findNearestContextual(@Nullable Failure t) {
        if (t == null) {
            return null;
        }
        if (t.getOriginal().getClass().getAnnotation(Contextual.class) != null || MultiCauseException.class.isAssignableFrom(t.getExceptionType())) {
            return t;
        }
        // Not multicause, so at most one cause.
        Optional<Failure> cause = t.getCauses().stream().findAny();
        return findNearestContextual(cause.orElse(null));
    }
}
