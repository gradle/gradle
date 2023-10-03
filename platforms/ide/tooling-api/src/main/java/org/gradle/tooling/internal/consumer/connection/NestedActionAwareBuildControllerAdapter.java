/*
 * Copyright 2020 the original author or authors.
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
import org.gradle.tooling.internal.protocol.InternalActionAwareBuildController;
import org.gradle.tooling.internal.protocol.InternalBuildControllerVersion2;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Supplier;

public class NestedActionAwareBuildControllerAdapter extends ParameterAwareBuildControllerAdapter {
    private final InternalActionAwareBuildController controller;

    public NestedActionAwareBuildControllerAdapter(InternalBuildControllerVersion2 buildController, ProtocolToModelAdapter adapter, ModelMapping modelMapping, File rootDir) {
        super(buildController, adapter, modelMapping, rootDir);
        this.controller = (InternalActionAwareBuildController) buildController;
    }

    @Override
    public boolean getCanQueryProjectModelInParallel(Class<?> modelType) {
        return controller.getCanQueryProjectModelInParallel(modelType);
    }

    @Override
    public <T> List<T> run(Collection<? extends BuildAction<? extends T>> buildActions) {
        List<Supplier<T>> wrappers = new ArrayList<Supplier<T>>(buildActions.size());
        for (final BuildAction<? extends T> action : buildActions) {
            wrappers.add(new Supplier<T>() {
                @Override
                public T get() {
                    return action.execute(NestedActionAwareBuildControllerAdapter.this);
                }
            });
        }
        return controller.run(wrappers);
    }
}
