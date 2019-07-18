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

import org.gradle.tooling.internal.protocol.ModelIdentifier;
import org.gradle.tooling.model.GradleProject;
import org.gradle.tooling.model.build.BuildEnvironment;
import org.gradle.tooling.model.eclipse.EclipseProject;
import org.gradle.tooling.model.eclipse.HierarchicalEclipseProject;
import org.gradle.tooling.model.gradle.GradleBuild;
import org.gradle.tooling.model.gradle.ProjectPublications;
import org.gradle.tooling.model.idea.BasicIdeaProject;
import org.gradle.tooling.model.idea.IdeaProject;
import org.gradle.tooling.model.internal.outcomes.ProjectOutcomes;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;

public class ModelMapping {
    private static final Map<Class<?>, String> MODEL_VERSIONS = new HashMap<Class<?>, String>();

    static {
        addModelVersions(MODEL_VERSIONS);
    }

    private static void addModelVersions(Map<Class<?>, String> map) {
        map.put(HierarchicalEclipseProject.class, "1.0-milestone-3");
        map.put(EclipseProject.class, "1.0-milestone-3");
        map.put(IdeaProject.class, "1.0-milestone-5");
        map.put(GradleProject.class, "1.0-milestone-5");
        map.put(BasicIdeaProject.class, "1.0-milestone-5");
        map.put(BuildEnvironment.class, "1.0-milestone-8");
        map.put(ProjectOutcomes.class, "1.2");
        map.put(Void.class, "1.0-milestone-3");
        map.put(GradleBuild.class, "1.8");
        map.put(ProjectPublications.class, "1.12");
    }

    public ModelIdentifier getModelIdentifierFromModelType(final Class<?> modelType) {
        if (modelType.equals(Void.class)) {
            return new DefaultModelIdentifier(ModelIdentifier.NULL_MODEL);
        }
        if (modelType.equals(ProjectOutcomes.class)) {
            return new DefaultModelIdentifier("org.gradle.tooling.model.outcomes.ProjectOutcomes");
        }
        return new DefaultModelIdentifier(modelType.getName());
    }

    @Nullable
    public String getVersionAdded(Class<?> modelType) {
        return MODEL_VERSIONS.get(modelType);
    }

    private static class DefaultModelIdentifier implements ModelIdentifier {
        private final String model;

        public DefaultModelIdentifier(String model) {
            this.model = model;
        }

        @Override
        public String toString() {
            return "tooling model " + model;
        }

        @Override
        public String getName() {
            return model;
        }
    }
}
