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

package org.gradle.workers.internal;

import org.gradle.workers.WorkerConfiguration;

public interface WorkerConfigurationInternal extends WorkerConfiguration {
    /**
     * Adds a set of packages to be shared from the worker runtime.
     *
     * @param sharedPackages shared package to be shared from the worker runtime
     */
    void sharedPackages(Iterable<String> sharedPackages);

    /**
     * Gets the packages to be shared from the worker runtime.
     *
     * @return the packages to be shared from the worker runtime
     */
    Iterable<String> getSharedPackages();


    /**
     * Sets the packages to be shared from the worker runtime.
     *
     * @param sharedPackages shared package to be shared from the worker runtime
     */
    void setSharedPackages(Iterable<String> sharedPackages);

}
