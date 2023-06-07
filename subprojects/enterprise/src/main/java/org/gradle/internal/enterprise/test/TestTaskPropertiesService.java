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

package org.gradle.internal.enterprise.test;

import org.gradle.api.tasks.testing.Test;
import org.gradle.internal.service.scopes.Scopes;
import org.gradle.internal.service.scopes.ServiceScope;

/**
 * Provides access to the configuration of {@link Test} tasks and allows
 * disabling storing their outputs in the build cache.
 */
@ServiceScope(Scopes.Project.class)
public interface TestTaskPropertiesService {

    /**
     * Collect the configuration of the given task.
     */
    TestTaskProperties collectProperties(Test task);

    /**
     * Avoid storing the task's outputs in the build cache.
     * <p>
     * This allows disabling storing a task's outputs in the build cache if it only executed a subset of tests
     * while still allowing the task to be loaded from the build cache if a prior execution with the same
     * cache key executed all tests.
     */
    void doNotStoreInCache(Test task);

}
