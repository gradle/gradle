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

import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.initialization.BuildGateToken;

public class GateControllingDeployment implements DeploymentInternal {
    private static final Logger LOGGER = Logging.getLogger(GateControllingDeployment.class);

    private final BuildGateToken.GateKeeper gateKeeper;
    private final DeploymentInternal delegate;

    public GateControllingDeployment(BuildGateToken buildGate, DeploymentInternal delegate) {
        this.gateKeeper = buildGate.createGateKeeper();
        this.delegate = delegate;
    }

    @Override
    public Status status() {
        if (gateKeeper != null) {
            LOGGER.debug("Opening gate");
            gateKeeper.open();
        }
        return delegate.status();
    }

    @Override
    public void outOfDate() {
        delegate.outOfDate();
    }

    @Override
    public void upToDate(Throwable failure) {
        delegate.upToDate(failure);

        if (gateKeeper != null) {
            LOGGER.debug("Closing gate");
            gateKeeper.close();
        }
    }
}
