/*
 * Copyright 2013 the original author or authors.
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

import org.gradle.initialization.BuildAction;
import org.gradle.initialization.BuildController;
import org.gradle.initialization.DefaultGradleLauncher;
import org.gradle.tooling.internal.protocol.*;

import java.io.Serializable;

class ClientProvidedBuildAction implements BuildAction<ToolingModel>, Serializable {
    private final ToolingModel action;

    public ClientProvidedBuildAction(ToolingModel action) {
        this.action = action;
    }

    public ToolingModel run(BuildController buildController) {
        PayloadSerializer payloadSerializer = ((DefaultGradleLauncher) buildController.getLauncher()).getGradle().getServices().get(PayloadSerializer.class);
        ClientBuildAction<?> action = (ClientBuildAction<?>) payloadSerializer.deserialize(this.action);
        Object result = action.execute(new InternalBuildController() {
            public BuildResult<?> getBuildModel() throws BuildExceptionVersion1 {
                throw new UnsupportedOperationException();
            }

            public BuildResult<?> getModel(ModelIdentifier modelIdentifier) throws BuildExceptionVersion1, InternalUnsupportedModelException {
                throw new UnsupportedOperationException();
            }
        });
        return payloadSerializer.serialize(result);
    }
}
