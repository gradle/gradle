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

package org.gradle.internal.model;

import org.gradle.internal.DisplayName;
import org.gradle.internal.buildoption.DefaultInternalOptions;
import org.gradle.internal.buildoption.InternalFlag;
import org.gradle.internal.buildoption.InternalOption;
import org.gradle.internal.buildoption.InternalOptions;
import org.gradle.internal.operations.BuildOperationRunner;
import org.gradle.internal.service.scopes.CrossBuildSessionParameters;
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;
import org.gradle.internal.work.Synchronizer;
import org.gradle.internal.work.WaitBuildOperationFiringSynchronizer;
import org.gradle.internal.work.WorkerLeaseService;

@ServiceScope(Scope.BuildSession.class)
public class StateTransitionControllerFactory {

    public static final InternalOption<Boolean> TRACE_SYNCHRONIZER_WAIT_OPTION = new InternalFlag("org.gradle.internal.operations.trace.synchronizer.wait", false);

    private final WorkerLeaseService workerLeaseService;
    private final boolean traceSynchronizerWait;
    private final BuildOperationRunner buildOperationRunner;

    public StateTransitionControllerFactory(
        WorkerLeaseService workerLeaseService,
        BuildOperationRunner buildOperationRunner,
        CrossBuildSessionParameters crossBuildSessionParameters
    ) {
        this.workerLeaseService = workerLeaseService;
        InternalOptions internalOptions = new DefaultInternalOptions(crossBuildSessionParameters.getStartParameter().getSystemPropertiesArgs());
        this.traceSynchronizerWait = internalOptions.getOption(TRACE_SYNCHRONIZER_WAIT_OPTION).get();
        this.buildOperationRunner = buildOperationRunner;
    }

    public <T extends StateTransitionController.State> StateTransitionController<T> newController(DisplayName displayName, T initialState) {
        Synchronizer synchronizer = workerLeaseService.newResource();
        if (traceSynchronizerWait) {
            synchronizer = new WaitBuildOperationFiringSynchronizer(displayName, synchronizer, buildOperationRunner);
        }
        return new StateTransitionController<>(displayName, initialState, synchronizer);
    }
}
