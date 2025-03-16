/*
 * Copyright 2021 the original author or authors.
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
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;

/**
 * Responsible for running the given action against a build tree.
 */
@ServiceScope(Scope.BuildTree.class)
public interface BuildTreeActionExecutor {
    /**
     * Runs the given action and returns the result. Failures should be packaged in the result.
     * When this method returns, all user code will have completed, including 'build finished' hooks.
     */
    BuildActionRunner.Result execute(BuildAction action, BuildTreeContext buildTreeContext);
}
