/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.api.internal.artifacts.configurations;

import com.google.common.collect.Maps;
import org.gradle.api.Action;
import org.gradle.api.artifacts.result.ResolvedComponentResult;
import org.gradle.api.internal.artifacts.result.ResolvedComponentResultInternal;
import org.gradle.internal.Actions;
import org.gradle.internal.operations.trace.CustomOperationTraceSerialization;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

class ResolveConfigurationResolutionBuildOperationResult implements ResolveConfigurationDependenciesBuildOperationType.Result, CustomOperationTraceSerialization {

    private static final Action<? super Throwable> FAIL_SAFE = Actions.doNothing();

    private final ResolvableDependenciesInternal incoming;
    private final boolean failSafe;

    ResolveConfigurationResolutionBuildOperationResult(ResolvableDependenciesInternal incoming, boolean failSafe) {
        this.incoming = incoming;
        this.failSafe = failSafe;
    }

    @Override
    public ResolvedComponentResult getRootComponent() {
        if (failSafe) {
            // When fail safe, we don't want the build operation listeners to fail whenever resolution throws an error
            // because:
            // 1. the `failed` method will have been called with the user facing error
            // 2. such an error still leads to a valid dependency graph
            return incoming.getResolutionResult(FAIL_SAFE).getRoot();
        }
        return incoming.getResolutionResult().getRoot();
    }

    @Override
    public String getRepositoryId(ResolvedComponentResult resolvedComponentResult) {
        return ((ResolvedComponentResultInternal) resolvedComponentResult).getRepositoryName();
    }

    @Override
    public Object getCustomOperationTraceSerializableModel() {
        Map<String, Object> model = new HashMap<String, Object>();
        model.put("resolvedDependenciesCount", getRootComponent().getDependencies().size());
        final Map<String, Map<String, String>> components = Maps.newHashMap();
        incoming.getResolutionResult(FAIL_SAFE).allComponents(new Action<ResolvedComponentResult>() {
            @Override
            public void execute(ResolvedComponentResult component) {
                components.put(
                    component.getId().getDisplayName(),
                    Collections.singletonMap("repoName", ((ResolvedComponentResultInternal) component).getRepositoryName())
                );
            }
        });
        model.put("components", components);
        return model;
    }

}
