/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.api.problems.internal;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Multimap;
import com.google.common.collect.MultimapBuilder;
import com.google.common.collect.Multimaps;
import org.gradle.api.Incubating;
import org.gradle.api.problems.Problem;
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * Holds references to exceptions reported via {@link org.gradle.problems.buildtree.ProblemReporter} and their associated problem reports.
 *
 * @since 8.12
 */
@Incubating
@ServiceScope(Scope.BuildSession.class)
public class ExceptionProblemRegistry {

    private final Multimap<Throwable, Problem> problemsForThrowables = Multimaps.synchronizedMultimap(MultimapBuilder.linkedHashKeys().linkedHashSetValues().<Throwable, Problem>build());

    public void onProblem(Throwable exception, Problem problem) {
        problemsForThrowables.put(exception, problem);
    }

    public ProblemLookup getProblemLookup() {
        return new DefaultProblemLookup();
    }

    /*
     * Workaround for the fact that the exception thrown by the worker is not the same instance as the one that was thrown by the build. With the lookup we can find the original exception by comparing
     * the stack frames. The comparison is expensive, so we only do when it's necessary (when the original exception does not contain the target and there's a matching class name and message).
     */
    private class DefaultProblemLookup implements ProblemLookup {


        private final Multimap<String, Throwable> lookup;

        DefaultProblemLookup() {
            this.lookup = initLookup(problemsForThrowables.keySet());
        }

        private Multimap<String, Throwable> initLookup(Set<Throwable> exceptions) {
            Multimap<String, Throwable> lookup = ArrayListMultimap.create();
            for (Throwable exception : exceptions) {
                lookup.put(key(exception), exception);
            }
            return lookup;
        }

        private String key(Throwable t) {
            return t.getClass().getName() + ":" + messageOf(t);
        }

        private String messageOf(Throwable t) {
            String result = "";
            try {
                String message = t.getMessage();
                result = message == null ? "" : message;
            } catch (RuntimeException ignore) {
                // ignore exceptions with faulty getMessage() implementation
            }
            return result;
        }

        @Override
        public Collection<Problem> findAll(Throwable t) {
            Throwable throwable = find(t);
            return throwable == null ? ImmutableList.<Problem>of() : ImmutableList.copyOf(problemsForThrowables.get(throwable));
        }

        @Nullable
        private Throwable find(Throwable t) {
            try {
                if (problemsForThrowables.keySet().contains(t)) {
                    return t;
                }
                Collection<Throwable> candidates = lookup.get(key(t));
                for (Throwable candidate : candidates) {
                    if (deepEquals(candidate, t, new ArrayList<Throwable>())) {
                        return candidate;
                    }
                }
            } catch (RuntimeException e) {
                e.printStackTrace();
                return null;
            }
            return null;
        }

        private boolean deepEquals(Throwable t1, Throwable t2, List<Throwable> seen) {
            if (seen.contains(t1) || seen.contains(t2)) {
                return false; // drop self-references to avoid infinite recursion
            }

            if (t1 == null && t2 == null) {
                return true; // equals if both null
            } else if (t1 == null || t2 == null) {
                return false; // either t1 or t2 is null
            }

            if (!t1.getClass().equals(t2.getClass()) || !messageOf(t1).equals(messageOf(t2))) {
                return false;
            }
            StackTraceElement[] s1 = t1.getStackTrace();
            StackTraceElement[] s2 = t2.getStackTrace();
            for (int i = 0; i < s1.length && i < s2.length; i++) {
                if (!isSackTraceElementEquals(s1[i], s2[i])) {
                    return false;
                }
            }
            seen.add(t1);
            seen.add(t2);
            return deepEquals(t1.getCause(), t2.getCause(), seen);
        }

        private boolean isSackTraceElementEquals(StackTraceElement s1, StackTraceElement s2) {
            if (!s1.getClassName().equals(s2.getClassName())) {
                return false;
            }
            String s1File = s1.getFileName();
            String s2File = s2.getFileName();
            if ((s1File == null && s2File != null) || (s1File != null && s2File == null)) {
                return false;
            } else if (s1File != null && s2File != null && !s1File.equals(s2File)) {
                return false;
            } else if (s1.getLineNumber() != s2.getLineNumber()) {
                return false;
            }

            return true;
        }

    }
}
