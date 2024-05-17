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

package org.gradle.launcher.exec;

import org.gradle.api.internal.StartParameterInternal;
import org.gradle.internal.Describables;
import org.gradle.internal.DisplayName;
import org.gradle.internal.buildtree.BuildActionModelRequirements;
import org.gradle.internal.hash.HashCode;
import org.gradle.internal.hash.Hasher;

import java.util.function.Supplier;

public abstract class AbstractToolingModelRequirements implements BuildActionModelRequirements {

    private final StartParameterInternal startParameter;
    private final boolean runsTasks;
    private final Supplier<HashCode> payloadHashProvider;

    public AbstractToolingModelRequirements(
        StartParameterInternal startParameter,
        boolean runsTasks,
        Supplier<HashCode> payloadHashProvider
    ) {
        this.startParameter = startParameter;
        this.runsTasks = runsTasks;
        this.payloadHashProvider = payloadHashProvider;
    }

    @Override
    public boolean isRunsTasks() {
        return runsTasks;
    }

    @Override
    public boolean isCreatesModel() {
        return true;
    }

    @Override
    public StartParameterInternal getStartParameter() {
        return startParameter;
    }

    @Override
    public DisplayName getActionDisplayName() {
        return Describables.of("creating tooling model");
    }

    @Override
    public DisplayName getConfigurationCacheKeyDisplayName() {
        return Describables.of("the requested model");
    }

    @Override
    public void appendKeyTo(Hasher hasher) {
        hasher.putByte(getActionTypeId());
        hasher.putHash(payloadHashProvider.get());
    }

    /**
     * Each inheriting action must provide a unique value.
     */
    protected abstract byte getActionTypeId();
}
