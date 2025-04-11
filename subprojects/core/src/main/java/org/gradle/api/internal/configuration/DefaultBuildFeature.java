/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.api.internal.configuration;

import org.gradle.api.configuration.BuildFeature;
import org.gradle.api.internal.StartParameterInternal;
import org.gradle.api.provider.Provider;

import java.util.function.Consumer;

public class DefaultBuildFeature implements BuildFeature {

    private final Provider<Boolean> requested;
    private final Provider<Boolean> active;
    private final Consumer<StartParameterInternal> disabler;
    private final String key;

    public DefaultBuildFeature(String key, Provider<Boolean> requested, Provider<Boolean> active, Consumer<StartParameterInternal> disabler) {
        this.requested = requested;
        this.active = active;
        this.disabler = disabler;
        this.key = key;
    }

    @Override
    public Provider<Boolean> getRequested() {
        return requested;
    }

    @Override
    public Provider<Boolean> getActive() {
        return active;
    }

    public void disable(StartParameterInternal startParameter) {
        disabler.accept(startParameter);
    }

    @Override
    public String toString() {
        return key + " - requested " + requested + " - active: " + active;
    }
}
