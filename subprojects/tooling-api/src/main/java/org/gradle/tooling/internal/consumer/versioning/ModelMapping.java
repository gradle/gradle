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

import org.gradle.tooling.internal.protocol.*;
import org.gradle.tooling.internal.protocol.eclipse.EclipseProjectVersion3;
import org.gradle.tooling.internal.protocol.eclipse.HierarchicalEclipseProjectVersion1;
import org.gradle.tooling.model.*;
import org.gradle.tooling.model.build.BuildEnvironment;
import org.gradle.tooling.model.eclipse.EclipseProject;
import org.gradle.tooling.model.eclipse.HierarchicalEclipseProject;
import org.gradle.tooling.model.idea.BasicIdeaProject;
import org.gradle.tooling.model.idea.IdeaProject;
import org.gradle.tooling.model.internal.TestModel;
import org.gradle.tooling.model.internal.outcomes.ProjectOutcomes;

import java.util.HashMap;
import java.util.Map;

/**
 * by Szczepan Faber, created at: 1/13/12
 */
public class ModelMapping {

    private static final Map<Class<? extends Model>, Class> MODEL_TYPE_MAP = new HashMap<Class<? extends Model>, Class>();

    static {
        MODEL_TYPE_MAP.putAll(getModelsUpToM6());
        MODEL_TYPE_MAP.putAll(getModelsPostM6());
    }

    static Map<Class<? extends Model>, Class> getModelsUpToM6() {
        Map<Class<? extends Model>, Class> map = new HashMap<Class<? extends Model>, Class>();
        map.put(HierarchicalEclipseProject.class, HierarchicalEclipseProjectVersion1.class);
        map.put(EclipseProject.class, EclipseProjectVersion3.class);
        map.put(IdeaProject.class, InternalIdeaProject.class);
        map.put(GradleProject.class, InternalGradleProject.class);
        map.put(BasicIdeaProject.class, InternalBasicIdeaProject.class);
        return map;
    }

    private static Map<Class<? extends Model>, Class> getModelsPostM6() {
        Map<Class<? extends Model>, Class> map = new HashMap<Class<? extends Model>, Class>();
        map.put(BuildEnvironment.class, InternalBuildEnvironment.class);
        map.put(TestModel.class, InternalTestModel.class);
        map.put(ProjectOutcomes.class, InternalProjectOutcomes.class);
        return map;
    }

    public Class getProtocolType(Class<? extends Model> viewType) {
        return MODEL_TYPE_MAP.get(viewType);
    }
}
