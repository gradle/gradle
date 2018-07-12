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

package org.gradle.internal.operations.trace;

import com.google.common.collect.ImmutableMap;
import org.gradle.api.artifacts.result.DependencyResult;
import org.gradle.api.artifacts.result.ResolvedComponentResult;
import org.gradle.api.artifacts.result.ResolvedDependencyResult;
import org.gradle.api.internal.artifacts.configurations.ResolveConfigurationDependenciesBuildOperationType;
import org.gradle.internal.operations.BuildOperationDescriptor;
import org.gradle.internal.operations.OperationFinishEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

class SerializedOperationFinish implements SerializedOperation {

    final long id;

    final long endTime;

    final Object result;
    final String resultClassName;

    final String failureMsg;

    SerializedOperationFinish(BuildOperationDescriptor descriptor, OperationFinishEvent finishEvent) {
        this.id = descriptor.getId().getId();
        this.endTime = finishEvent.getEndTime();
        this.result = transform(finishEvent.getResult());
        this.resultClassName = result == null ? null : finishEvent.getResult().getClass().getName();
        this.failureMsg = finishEvent.getFailure() == null ? null : finishEvent.getFailure().toString();
    }

    private Object transform(Object result) {
        if (result instanceof ResolveConfigurationDependenciesBuildOperationType.Result) {
            Set<ResolvedComponentResult> alreadySeen = new HashSet<ResolvedComponentResult>();
            ResolveConfigurationDependenciesBuildOperationType.Result cast = (ResolveConfigurationDependenciesBuildOperationType.Result) result;
            Map<String, Object> transform = new HashMap<String, Object>();
            transform.put("resolvedDependenciesCount", cast.getRootComponent().getDependencies().size());
            Map<String, List<Object>> components = new HashMap<String, List<Object>>();
            walk(cast.getRootComponent(), components, alreadySeen);
            transform.put("components", components);
            return transform;
        }

        return result;
    }

    private void walk(ResolvedComponentResult component, Map<String, List<Object>> components, Set<ResolvedComponentResult> alreadySeen) {
        if (alreadySeen.contains(component)) {
            return;
        }
        alreadySeen.add(component);
        String componentDisplayName = component.getId().getDisplayName();
        List<Object> componentDetails;
        if (components.containsKey(componentDisplayName)) {
            componentDetails = components.get(componentDisplayName);
        } else {
            componentDetails = new ArrayList<Object>();
            components.put(componentDisplayName, componentDetails);
        }
        componentDetails.add(Collections.singletonMap("repoId", component.getRepositoryId()));
        for (DependencyResult dependencyResult : component.getDependencies()) {
            if (dependencyResult instanceof ResolvedDependencyResult) {
                walk(((ResolvedDependencyResult) dependencyResult).getSelected(), components, alreadySeen);
            }
        }
    }

    SerializedOperationFinish(Map<String, ?> map) {
        this.id = ((Integer) map.get("id")).longValue();
        this.endTime = (Long) map.get("endTime");
        this.result = map.get("result");
        this.resultClassName = (String) map.get("resultClassName");
        this.failureMsg = (String) map.get("failure");
    }

    public Map<String, ?> toMap() {
        ImmutableMap.Builder<String, Object> map = ImmutableMap.builder();

        // Order is optimised for humans looking at the log.

        map.put("id", id);

        if (result != null) {
            map.put("result", result);
            map.put("resultClassName", resultClassName);
        }

        if (failureMsg != null) {
            map.put("failure", failureMsg);
        }

        map.put("endTime", endTime);

        return map.build();
    }

}
