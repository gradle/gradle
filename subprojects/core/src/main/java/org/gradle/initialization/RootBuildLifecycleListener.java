/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.initialization;

import org.gradle.internal.service.scopes.EventScope;
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.StatefulListener;

/**
 * A listener that is notified when a root build is started and completed. No more than one root build may run at a given time.
 *
 * A root build may contain zero or more nested builds, such as `buildSrc` or included builds.
 *
 * This listener type is available to services from build tree up to global services.
 */
@EventScope(Scope.BuildTree.class)
@StatefulListener
public interface RootBuildLifecycleListener {
    /**
     * Called at the start of the root build, immediately after the creation of the root build services.
     */
    void afterStart();

    /**
     * Called at the completion of the root build, immediately before destruction of the root build services.
     */
    void beforeComplete();
}
