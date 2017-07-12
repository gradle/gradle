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

package org.gradle.internal.concurrent

import org.gradle.concurrent.ParallelismConfiguration


class ParallelismConfigurationManagerFixture implements ParallelismConfigurationManager {
    ParallelismConfiguration parallelismConfiguration
    List<ParallelismConfigurationListener> listeners = []

    ParallelismConfigurationManagerFixture(boolean isParallelEnabled, int maxWorkers) {
        this.parallelismConfiguration = new DefaultParallelismConfiguration(isParallelEnabled, maxWorkers)
    }

    ParallelismConfigurationManagerFixture(ParallelismConfiguration parallelismConfiguration) {
        this.parallelismConfiguration = parallelismConfiguration
    }

    @Override
    ParallelismConfiguration getParallelismConfiguration() {
        return parallelismConfiguration
    }

    @Override
    void setParallelismConfiguration(ParallelismConfiguration parallelismConfiguration) {
        this.parallelismConfiguration = parallelismConfiguration
    }

    @Override
    void addListener(ParallelismConfigurationListener listener) {
        listeners.add(listener)
    }

    @Override
    void removeListener(ParallelismConfigurationListener listener) {
        listeners.remove(listener)
    }
}
