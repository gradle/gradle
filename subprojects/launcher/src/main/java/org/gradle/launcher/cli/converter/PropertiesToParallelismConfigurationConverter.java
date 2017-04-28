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

package org.gradle.launcher.cli.converter;

import org.gradle.StartParameter;
import org.gradle.initialization.ParallelismConfiguration;
import org.gradle.launcher.daemon.configuration.GradleProperties;

import java.util.Map;

import static org.gradle.launcher.daemon.configuration.GradleProperties.isTrue;

public class PropertiesToParallelismConfigurationConverter {
    public ParallelismConfiguration convert(Map<String, String> properties, ParallelismConfiguration parallelismConfiguration) {
        String parallel = properties.get(GradleProperties.PARALLEL_PROPERTY);
        if (isTrue(parallel)) {
            parallelismConfiguration.setParallelProjectExecutionEnabled(true);
        }

        String workers = properties.get(GradleProperties.WORKERS_PROPERTY);
        if (workers != null) {
            try {
                int workerCount = Integer.parseInt(workers);
                if (workerCount < 1) {
                    invalidMaxWorkersPropValue(workers);
                }
                parallelismConfiguration.setMaxWorkerCount(workerCount);
            } catch (NumberFormatException e) {
                invalidMaxWorkersPropValue(workers);
            }
        }

        return parallelismConfiguration;
    }

    private StartParameter invalidMaxWorkersPropValue(String value) {
        throw new IllegalArgumentException(String.format("Value '%s' given for %s system property is invalid (must be a positive, non-zero, integer)", value, GradleProperties.WORKERS_PROPERTY));
    }
}
