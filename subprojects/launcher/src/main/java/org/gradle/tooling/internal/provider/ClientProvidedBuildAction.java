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

import org.gradle.StartParameter;
import org.gradle.internal.invocation.BuildAction;

import java.io.Serializable;

public class ClientProvidedBuildAction implements BuildAction, Serializable {
    private final StartParameter startParameter;
    private final SerializedPayload action;
    private final BuildClientSubscriptions clientSubscriptions;

    public ClientProvidedBuildAction(StartParameter startParameter, SerializedPayload action, BuildClientSubscriptions clientSubscriptions) {
        this.startParameter = startParameter;
        this.action = action;
        this.clientSubscriptions = clientSubscriptions;
    }

    @Override
    public StartParameter getStartParameter() {
        return startParameter;
    }

    public SerializedPayload getAction() {
        return action;
    }

    public BuildClientSubscriptions getClientSubscriptions() {
        return clientSubscriptions;
    }
}
