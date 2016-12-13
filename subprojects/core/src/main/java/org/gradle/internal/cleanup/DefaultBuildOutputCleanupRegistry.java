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

package org.gradle.internal.cleanup;

import org.gradle.util.CollectionUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class DefaultBuildOutputCleanupRegistry implements BuildOutputCleanupRegistry {

    private final List<BuildOutputCleanupStrategy> strategies = new ArrayList<BuildOutputCleanupStrategy>();
    private final List<File> outputs = new ArrayList<File>();

    @Override
    public void registerStrategy(BuildOutputCleanupStrategy strategy) {
        strategies.add(strategy);
    }

    @Override
    public List<BuildOutputCleanupStrategy> getStrategies() {
        return strategies;
    }

    @Override
    public void registerOutputs(File... outputs) {
        registerOutputs(CollectionUtils.toList(outputs));
    }

    @Override
    public void registerOutputs(List<File> outputs) {
        this.outputs.addAll(outputs);
    }

    @Override
    public List<File> getOutputs() {
        return outputs;
    }
}
