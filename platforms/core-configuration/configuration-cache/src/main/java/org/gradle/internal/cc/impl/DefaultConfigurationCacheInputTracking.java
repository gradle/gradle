/*
 * Copyright 2026 Gradle and contributors.
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

package org.gradle.internal.cc.impl;

import org.gradle.api.configuration.ConfigurationCacheInputTracking;
import org.gradle.internal.UncheckedException;
import org.jspecify.annotations.NullMarked;

import java.util.concurrent.Callable;

@NullMarked
public final class DefaultConfigurationCacheInputTracking implements ConfigurationCacheInputTracking {

    private final InputTrackingState inputTrackingState;

    public DefaultConfigurationCacheInputTracking(InputTrackingState inputTrackingState) {
        this.inputTrackingState = inputTrackingState;
    }

    @Override
    public <T> T withInputTrackingDisabledUnsafe(Callable<? extends T> action) {
        inputTrackingState.disableForCurrentThread();
        try {
            return action.call();
        } catch (Exception ex) {
            throw UncheckedException.throwAsUncheckedException(ex);
        } finally {
            inputTrackingState.restoreForCurrentThread();
        }
    }
}
