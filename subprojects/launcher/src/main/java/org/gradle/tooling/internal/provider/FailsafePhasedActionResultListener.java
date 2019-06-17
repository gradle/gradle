/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.tooling.internal.provider;

import org.gradle.internal.event.ListenerNotificationException;
import org.gradle.tooling.internal.protocol.PhasedActionResult;
import org.gradle.tooling.internal.protocol.PhasedActionResultListener;

import java.util.ArrayList;
import java.util.List;

/**
 * Listener that will collect failures from the delegate listener and rethrow them in the right moment of the build.
 */
public class FailsafePhasedActionResultListener implements PhasedActionResultListener {
    private final PhasedActionResultListener delegate;
    private List<Throwable> listenerFailures = new ArrayList<Throwable>();

    public FailsafePhasedActionResultListener(PhasedActionResultListener delegate) {
        this.delegate = delegate;
    }

    @Override
    public void onResult(PhasedActionResult<?> event) {
        try {
            delegate.onResult(event);
        } catch (Throwable t) {
            listenerFailures.add(t);
        }
    }

    public void rethrowErrors() {
        if (!listenerFailures.isEmpty()) {
            throw new ListenerNotificationException(null, "One or more build phasedAction listeners failed with an exception.", listenerFailures);
        }
    }
}
