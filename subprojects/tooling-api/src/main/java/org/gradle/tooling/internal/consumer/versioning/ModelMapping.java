/*
 * Copyright 2011 the original author or authors.
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

package org.gradle.tooling.internal.consumer.versioning;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import org.gradle.tooling.internal.protocol.*;
import org.gradle.tooling.internal.protocol.eclipse.EclipseProjectVersion3;
import org.gradle.tooling.internal.protocol.eclipse.HierarchicalEclipseProjectVersion1;
import org.gradle.tooling.model.GradleProject;
import org.gradle.tooling.model.build.BuildEnvironment;
import org.gradle.tooling.model.eclipse.EclipseProject;
import org.gradle.tooling.model.eclipse.HierarchicalEclipseProject;
import org.gradle.tooling.model.idea.BasicIdeaProject;
import org.gradle.tooling.model.idea.IdeaProject;
import org.gradle.tooling.model.internal.outcomes.ProjectOutcomes;

import java.util.Map;

/**
 * by Szczepan Faber, created at: 1/13/12
 */
public class ModelMapping {
    private static final BiMap<Class<?>, Class<?>> MODEL_TO_PROTOCOL_MAP = HashBiMap.create();
    private static final BiMap<Class<?>, String> MODEL_NAME_MAP = HashBiMap.create();

    static {
        addModelToProtocolMappings(MODEL_TO_PROTOCOL_MAP);
        addModelNameMappings(MODEL_NAME_MAP);
    }

    static void addModelToProtocolMappings(Map<Class<?>, Class<?>> map) {
        map.put(HierarchicalEclipseProject.class, HierarchicalEclipseProjectVersion1.class);
        map.put(EclipseProject.class, EclipseProjectVersion3.class);
        map.put(IdeaProject.class, InternalIdeaProject.class);
        map.put(GradleProject.class, InternalGradleProject.class);
        map.put(BasicIdeaProject.class, InternalBasicIdeaProject.class);
        map.put(BuildEnvironment.class, InternalBuildEnvironment.class);
        map.put(ProjectOutcomes.class, InternalProjectOutcomes.class);
        map.put(Void.class, Void.class);
    }

    static void addModelNameMappings(Map<Class<?>, String> map) {
        map.put(HierarchicalEclipseProject.class, "org.gradle.tooling.model.eclipse.HierarchicalEclipseProject");
        map.put(EclipseProject.class, "org.gradle.tooling.model.eclipse.EclipseProject");
        map.put(IdeaProject.class, "org.gradle.tooling.model.idea.IdeaProject");
        map.put(GradleProject.class, "org.gradle.tooling.model.GradleProject");
        map.put(BasicIdeaProject.class, "org.gradle.tooling.model.idea.BasicIdeaProject");
        map.put(BuildEnvironment.class, "org.gradle.tooling.model.build.BuildEnvironment");
        map.put(ProjectOutcomes.class, "org.gradle.tooling.model.outcomes.ProjectOutcomes");
        map.put(Void.class, Void.class.getName());
    }

    public Class<?> getProtocolType(Class<?> modelType) {
        return MODEL_TO_PROTOCOL_MAP.get(modelType);
    }

    public String getModelName(Class<?> modelType) {
        return MODEL_NAME_MAP.get(modelType);
    }

    public String getModelNameFromProtocolType(Class<?> protocolType) {
        Class<?> modelType = MODEL_TO_PROTOCOL_MAP.inverse().get(protocolType);
        if (modelType == null) {
            return null;
        }
        return MODEL_NAME_MAP.get(modelType);
    }

    public Class<?> getProtocolTypeFromModelName(String name) {
        Class<?> modelType = MODEL_NAME_MAP.inverse().get(name);
        if (modelType == null) {
            return null;
        }
        return getProtocolType(modelType);
    }
}
