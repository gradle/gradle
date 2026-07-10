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

import org.gradle.internal.service.scopes.EventScope;
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.StatefulListener;

@EventScope(Scope.BuildTree.class)
@StatefulListener
public interface BuildTreeLifecycleListener {
    /**
     * Called after the build tree has been created, just after the services have been created.
     *
     * This method is called before the root build operation has started, so implementations should not perform any expensive work
     * and should not run any user code.
     */
    default void afterStart() {
    }

    /**
     * Called just before the build tree is finished with, just prior to closing the build tree services.
     *
     * This method is called after the root build operation has completed, so implementations should not perform any expensive work
     * and should not run any user code.
     */
    default void beforeStop() {
    }
}
