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

package org.gradle.integtests.tooling.fixture

import org.gradle.tooling.events.ProgressEvent
import org.gradle.tooling.events.StatusEvent

// This is a separate class because StatusEvent is not available for all versions of the tooling API
class ProgressEventsWithStatus extends ProgressEvents {
    @Override
    protected void otherEvent(ProgressEvent event, Operation operation) {
        if (event instanceof StatusEvent) {
            operation.statusEvents.add(new OperationStatus(event))
        } else {
            super.otherEvent(event, operation)
        }
    }
}
