/*
 * Copyright 2024 the original author or authors.
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

import org.gradle.internal.cc.impl.InputTrackingState;
import org.gradle.internal.enterprise.DevelocityPluginUnsafeConfigurationService;

import javax.inject.Inject;
import java.util.function.Supplier;

public class DefaultDevelocityPluginUnsafeConfigurationService implements DevelocityPluginUnsafeConfigurationService {

    private final InputTrackingState inputTrackingState;

    @Inject
    public DefaultDevelocityPluginUnsafeConfigurationService(InputTrackingState inputTrackingState) {
        this.inputTrackingState = inputTrackingState;
    }

    @Override
    public <T> T withConfigurationInputTrackingDisabled(Supplier<T> supplier) {
        inputTrackingState.disableForCurrentThread();
        try {
            return supplier.get();
        } finally {
            inputTrackingState.restoreForCurrentThread();
        }
    }
}
