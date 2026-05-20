/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.internal.buildtree;

import org.gradle.internal.invocation.BuildAction;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;

/**
 * Responsible for executing a {@link BuildAction} against a build-tree.
 * <p>
 * Used by the build session layer (via {@link org.gradle.internal.session.BuildSessionActionExecutor})
 * to run the requested action one or more times within the session's lifetime.
 */
@ServiceScope(Scope.BuildSession.class)
public interface BuildTreeActionExecutor {

    /**
     * Creates a new build tree, executes the given action against it,
     * and dismantles the build tree state before returning the result.
     * <p>
     * The executor handles all build action types: task execution, Tooling API model building,
     * and client-provided
     * {@linkplain org.gradle.tooling.BuildActionExecuter phased actions}
     * that can build models and run tasks.
     * <p>
     * For a regular (non-continuous) build invocation this method is called exactly once.
     * When running with {@code --continuous}, it is called once per build iteration;
     * each call gets its own build tree that is set up and torn down independently.
     * <p>
     * A new {@link org.gradle.internal.buildtree.BuildTreeState} is created for each
     * invocation, rooted in the
     * {@linkplain org.gradle.internal.service.scopes.CrossBuildSessionParameters#getUserActionRootDirectory()
     * user-action root directory}.
     * The {@link BuildModelParameters} are derived from the action's requirements and on-disk configuration
     * on each invocation, so they may differ between iterations if either of those had changed.
     * For instance, the user can toggle Gradle features in the {@code gradle.properties} files.
     * <p>
     * The action is executed as a worker thread, which allows the thread to acquire
     * resource locks (e.g. worker leases) needed by the build infrastructure.
     *
     * <h4>Failure handling</h4>
     * Build failures that occur during action execution are normally packed into the returned
     * {@link BuildActionRunner.Result}. However, if the build tree teardown itself fails,
     * the teardown failure is combined with any prior build failure and thrown as an exception
     * rather than being returned in the result. This is because failure logging for thrown
     * exceptions happens at the session level, while result-based failure logging happens
     * within the build tree scope.
     *
     * @param action the build action to execute
     * @param buildSessionServices the build-session-scoped service registry,
     *     used as the parent for build-tree services
     * @return the result of executing the action, containing either a client result
     *     or a build failure
     */
    BuildActionRunner.Result runBuildTreeAction(BuildAction action, ServiceRegistry buildSessionServices);

}
