/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.api.tasks;

import org.gradle.api.Buildable;
import org.gradle.api.Task;
import org.hamcrest.*;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static org.hamcrest.CoreMatchers.equalTo;

public class TaskDependencyMatchers {
    @Factory
    public static Matcher<Task> dependsOn(final String... tasks) {
        return dependsOn(equalTo(new HashSet<String>(Arrays.asList(tasks))));
    }

    @Factory
    public static Matcher<Task> dependsOn(Matcher<? extends Iterable<String>> matcher) {
        return dependsOn(matcher, false);
    }

    @Factory
    public static Matcher<Task> dependsOnPaths(Matcher<? extends Iterable<String>> matcher) {
        return dependsOn(matcher, true);
    }

    private static Matcher<Task> dependsOn(final Matcher<? extends Iterable<String>> matcher, final boolean matchOnPaths) {
        return new BaseMatcher<Task>() {
            @Override
            public boolean matches(Object o) {
                Task task = (Task) o;
                Set<String> names = new HashSet<String>();
                Set<? extends Task> depTasks = task.getTaskDependencies().getDependencies(task);
                for (Task depTask : depTasks) {
                    names.add(matchOnPaths ? depTask.getPath() : depTask.getName());
                }
                boolean matches = matcher.matches(names);
                if (!matches) {
                    StringDescription description = new StringDescription();
                    matcher.describeTo(description);
                    System.out.println(String.format("expected %s, got %s.", description.toString(), names));
                }
                return matches;
            }

            @Override
            public void describeTo(Description description) {
                description.appendText("a Task that depends on ").appendDescriptionOf(matcher);
            }
        };
    }

    @Factory
    public static <T extends Buildable> Matcher<T> builtBy(String... tasks) {
        return builtBy(equalTo(new HashSet<String>(Arrays.asList(tasks))));
    }

    @Factory
    public static <T extends Buildable> Matcher<T> builtBy(final Matcher<? extends Iterable<String>> matcher) {
        return new BaseMatcher<T>() {
            @Override
            public boolean matches(Object o) {
                Buildable task = (Buildable) o;
                Set<String> names = new HashSet<String>();
                Set<? extends Task> depTasks = task.getBuildDependencies().getDependencies(null);
                for (Task depTask : depTasks) {
                    names.add(depTask.getName());
                }
                boolean matches = matcher.matches(names);
                if (!matches) {
                    StringDescription description = new StringDescription();
                    matcher.describeTo(description);
                    System.out.println(String.format("expected %s, got %s.", description.toString(), names));
                }
                return matches;
            }

            @Override
            public void describeTo(Description description) {
                description.appendText("a Buildable that is built by ").appendDescriptionOf(matcher);
            }
        };
    }
}
