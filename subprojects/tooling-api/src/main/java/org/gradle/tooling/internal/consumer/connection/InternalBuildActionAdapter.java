/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.tooling.internal.consumer.connection;

import org.gradle.tooling.BuildAction;
import org.gradle.tooling.BuildController;
import org.gradle.tooling.internal.adapter.ProtocolToModelAdapter;
import org.gradle.tooling.internal.consumer.converters.ConsumerTargetTypeProvider;
import org.gradle.tooling.internal.consumer.versioning.ModelMapping;
import org.gradle.tooling.internal.consumer.versioning.VersionDetails;
import org.gradle.tooling.internal.protocol.InternalBuildAction;
import org.gradle.tooling.internal.protocol.InternalBuildController;
import org.gradle.tooling.model.gradle.BuildInvocations;

import java.io.File;

/**
 * Adapter to create {@link org.gradle.tooling.internal.protocol.InternalBuildAction}
 * from an instance of {@link org.gradle.tooling.BuildAction}.
 * Used by consumer connections 1.8+.
 */
public class InternalBuildActionAdapter<T> implements InternalBuildAction<T> {
    private final BuildAction<T> action;
    private final File rootDir;
    private final VersionDetails versionDetails;

    public InternalBuildActionAdapter(BuildAction<T> action, File rootDir, VersionDetails versionDetails) {
        this.action = action;
        this.rootDir = rootDir;
        this.versionDetails = versionDetails;
    }

    public T execute(final InternalBuildController buildController) {
        ProtocolToModelAdapter protocolToModelAdapter = new ProtocolToModelAdapter(new ConsumerTargetTypeProvider());
        BuildController buildControllerAdapter = new BuildControllerAdapter(protocolToModelAdapter, buildController, new ModelMapping(), rootDir);
        if (!versionDetails.maySupportModel(BuildInvocations.class)) {
            buildControllerAdapter= new BuildInvocationsAdapterController(protocolToModelAdapter, buildControllerAdapter);
        }
        return action.execute(buildControllerAdapter);
    }
}
