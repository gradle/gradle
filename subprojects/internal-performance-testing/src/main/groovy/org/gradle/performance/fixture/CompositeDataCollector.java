/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.performance.fixture;

import com.google.common.collect.Lists;
import org.gradle.internal.concurrent.CompositeStoppable;
import org.gradle.internal.concurrent.Stoppable;
import org.gradle.performance.measure.MeasuredOperation;

import java.io.File;
import java.util.Arrays;
import java.util.List;

public class CompositeDataCollector implements DataCollector, Stoppable {
    private final List<DataCollector> collectors;

    public CompositeDataCollector(DataCollector... collectors) {
        this.collectors = Arrays.asList(collectors);
    }

    @Override
    public List<String> getAdditionalJvmOpts(File workingDir) {
        List<String> additional = Lists.newLinkedList();
        for (DataCollector collector : collectors) {
            additional.addAll(collector.getAdditionalJvmOpts(workingDir));
        }
        return additional;
    }

    @Override
    public List<String> getAdditionalArgs(File workingDir) {
        List<String> additional = Lists.newLinkedList();
        for (DataCollector collector : collectors) {
            additional.addAll(collector.getAdditionalArgs(workingDir));
        }
        return additional;
    }

    public void collect(BuildExperimentInvocationInfo invocationInfo, MeasuredOperation operation) {
        for (DataCollector collector : collectors) {
            collector.collect(invocationInfo, operation);
        }
    }

    @Override
    public void stop() {
        CompositeStoppable.stoppable(collectors).stop();
    }
}
