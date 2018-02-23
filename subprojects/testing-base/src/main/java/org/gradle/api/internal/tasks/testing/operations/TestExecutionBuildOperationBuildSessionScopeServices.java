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

package org.gradle.api.internal.tasks.testing.operations;

import org.gradle.internal.event.ListenerManager;
import org.gradle.internal.operations.BuildOperationIdFactory;
import org.gradle.internal.progress.BuildOperationListener;
import org.gradle.internal.service.ServiceRegistration;
import org.gradle.internal.time.Clock;

public class TestExecutionBuildOperationBuildSessionScopeServices {

    TestListenerBuildOperationAdapter createTestListenerBuildOperationAdapter(ListenerManager listener, BuildOperationIdFactory buildOperationIdFactory, Clock clock) {
        return new TestListenerBuildOperationAdapter(listener.getBroadcaster(BuildOperationListener.class), buildOperationIdFactory, clock);
    }

    void configure(ServiceRegistration serviceRegistration, ListenerManager listenerManager, TestListenerBuildOperationAdapter testListenerBuildOperationAdapter) {
        listenerManager.addListener(testListenerBuildOperationAdapter);
    }

}
