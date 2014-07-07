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
import org.gradle.tooling.internal.adapter.ProtocolToModelAdapter;
import org.gradle.tooling.internal.consumer.versioning.ModelMapping;
import org.gradle.tooling.internal.protocol.InternalBuildAction;
import org.gradle.tooling.internal.protocol.InternalBuildController;

/**
 * Adapter to create {@link org.gradle.tooling.internal.protocol.InternalBuildAction}
 * from an instance of {@link org.gradle.tooling.BuildAction}.
 * Used by consumer connections 1.8+.
 */
class InternalBuildActionAdapter<T> implements InternalBuildAction<T> {
    private final BuildAction<T> action;
    private final ProtocolToModelAdapter adapter;

    public InternalBuildActionAdapter(BuildAction<T> action, ProtocolToModelAdapter adapter) {
        this.action = action;
        this.adapter = adapter;
    }

    public T execute(final InternalBuildController buildController) {
        return action.execute(new BuildControllerAdapter(adapter, buildController, new ModelMapping()));
    }
}