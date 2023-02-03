/*
 * Copyright 2007-2008 the original author or authors.
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
package org.gradle.api;

import org.gradle.internal.service.scopes.EventScope;
import org.gradle.internal.service.scopes.Scopes;

/**
 * <p>An {@code ProjectEvaluationListener} is notified when a project is evaluated. You add can add an {@code
 * ProjectEvaluationListener} to a {@link org.gradle.api.invocation.Gradle} using {@link
 * org.gradle.api.invocation.Gradle#addProjectEvaluationListener(ProjectEvaluationListener)}.</p>
 */
@EventScope(Scopes.Build.class)
public interface ProjectEvaluationListener {
    /**
     * This method is called immediately before a project is evaluated.
     *
     * @param project The which is to be evaluated. Never null.
     */
    void beforeEvaluate(Project project);

    /**
     * <p>This method is called when a project has been evaluated, and before the evaluated project is made available to
     * other projects.</p>
     *
     * @param project The project which was evaluated. Never null.
     * @param state The project evaluation state. If project evaluation failed, the exception is available in this
     * state. Never null.
     */
    void afterEvaluate(Project project, ProjectState state);
}
