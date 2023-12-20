/*
 * Copyright 2023 the original author or authors.
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

import org.gradle.tooling.internal.adapter.ProtocolToModelAdapter;
import org.gradle.tooling.internal.consumer.versioning.ModelMapping;
import org.gradle.tooling.internal.consumer.versioning.VersionDetails;
import org.gradle.tooling.internal.protocol.InternalBuildControllerVersion2;
import org.gradle.tooling.internal.protocol.InternalStreamedValueRelay;

import java.io.File;

public class StreamingAwareBuildControllerAdapter extends NestedActionAwareBuildControllerAdapter {
    private final InternalStreamedValueRelay relay;

    public StreamingAwareBuildControllerAdapter(InternalBuildControllerVersion2 buildController, ProtocolToModelAdapter adapter, ModelMapping modelMapping, VersionDetails gradleVersion, File rootDir) {
        super(buildController, adapter, modelMapping, gradleVersion, rootDir);
        this.relay = (InternalStreamedValueRelay) buildController;
    }

    @Override
    public <T> void send(T value) {
        relay.dispatch(value);
    }
}
