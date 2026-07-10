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

package org.gradle.internal.enterprise.impl;

import org.gradle.internal.enterprise.GradleEnterprisePluginBackgroundJobExecutors;
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;

@ServiceScope(Scope.BuildTree.class)
public interface GradleEnterprisePluginBackgroundJobExecutorsInternal extends GradleEnterprisePluginBackgroundJobExecutors {

    /**
     * Shuts the executors down.
     * All executors immediately stops accepting new jobs. The method blocks until already submitted jobs complete.
     *
     * @throws RuntimeException any exception or error thrown by a job is rethrown from this method, potentially wrapped as a RuntimeException
     */
    void shutdown();

}
