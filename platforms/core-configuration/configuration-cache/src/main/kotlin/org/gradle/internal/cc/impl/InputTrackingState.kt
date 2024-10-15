/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.internal.cc.impl

import com.google.common.base.Preconditions
import org.gradle.internal.extensions.stdlib.threadLocal
import org.gradle.internal.service.scopes.Scope
import org.gradle.internal.service.scopes.ServiceScope


/**
 * Maintains the current state of the build configuration input tracking.
 * Input tracking may be disabled for a particular thread to avoid recording inputs that doesn't
 * directly affect the configuration.
 * For example, accessing environment variable inside the `ValueSource` being obtained at
 * the configuration time is not really an input, because the value of the value source itself
 * becomes part of the configuration cache fingerprint.
 */
@ServiceScope(Scope.BuildTree::class)
class InputTrackingState {
    private
    var inputTrackingDisabledCounterForThread by threadLocal { 0 }

    /**
     * Returns input tracking status for the current thread.
     */
    fun isEnabledForCurrentThread(): Boolean = inputTrackingDisabledCounterForThread == 0

    /**
     * Disables input tracking for the current thread. Multiple calls to this method "stack", so if
     * this method was called twice then [restoreForCurrentThread] should also be called twice to
     * enable input tracking back.
     */
    fun disableForCurrentThread() {
        ++inputTrackingDisabledCounterForThread
    }

    /**
     * Restores the input tracking state to the state it was in before the last call to
     * [disableForCurrentThread].
     */
    fun restoreForCurrentThread() {
        Preconditions.checkState(inputTrackingDisabledCounterForThread > 0, "Restore input tracking state without prior disable is detected")
        --inputTrackingDisabledCounterForThread
    }
}
