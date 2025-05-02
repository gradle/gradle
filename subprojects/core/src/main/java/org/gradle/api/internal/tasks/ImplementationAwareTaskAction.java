/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.api.internal.tasks;

import org.gradle.api.Action;
import org.gradle.api.Task;
import org.gradle.internal.hash.ClassLoaderHierarchyHasher;
import org.gradle.internal.snapshot.impl.ImplementationSnapshot;

public interface ImplementationAwareTaskAction extends Action<Task> {

    /**
     * Returns the implementation snapshot for the action.
     *
     * This can be the implementation of the implementing class, or of some delegate action.
     */
    ImplementationSnapshot getActionImplementation(ClassLoaderHierarchyHasher hasher);
}
