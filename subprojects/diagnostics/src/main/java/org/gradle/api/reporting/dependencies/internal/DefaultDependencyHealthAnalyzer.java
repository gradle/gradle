/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.api.reporting.dependencies.internal;

import org.gradle.api.internal.dependencies.DependencyHealthCollector;
import org.gradle.api.internal.dependencies.DependencyHealthStatistics;

public class DefaultDependencyHealthAnalyzer implements DependencyHealthCollector {
    @Override
    public DependencyHealthStatistics getStatistics() {
        return new DefaultDependencyHealthStatistics();
    }

    private static class DefaultDependencyHealthStatistics implements DependencyHealthStatistics {

        @Override
        public long getTotal() {
            return 0;
        }

        @Override
        public long getSuppressed() {
            return 0;
        }

        @Override
        public long getLow() {
            return 0;
        }

        @Override
        public long getMedium() {
            return 0;
        }

        @Override
        public long getHigh() {
            return 0;
        }

        @Override
        public long getCritical() {
            return 0;
        }
    }
}
