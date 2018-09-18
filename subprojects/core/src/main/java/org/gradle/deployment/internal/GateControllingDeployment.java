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

package org.gradle.deployment.internal;

import org.gradle.initialization.ContinuousExecutionGate;

class GateControllingDeployment implements DeploymentInternal {
    private final ContinuousExecutionGate.GateKeeper gateKeeper;
    private final DeploymentInternal delegate;

    GateControllingDeployment(ContinuousExecutionGate continuousExecutionGate, DeploymentInternal delegate) {
        this.gateKeeper = continuousExecutionGate.createGateKeeper();
        this.delegate = delegate;
    }

    @Override
    public Status status() {
        gateKeeper.open();
        Status status = delegate.status();
        if (!status.hasChanged()) {
            gateKeeper.close();
        }
        return status;
    }

    @Override
    public void outOfDate() {
        delegate.outOfDate();
    }

    @Override
    public void upToDate(Throwable failure) {
        delegate.upToDate(failure);
        gateKeeper.close();
    }
}
