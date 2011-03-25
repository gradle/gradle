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

package org.gradle.tooling.internal.provider;

import org.gradle.BuildAdapter;
import org.gradle.api.Project;
import org.gradle.api.internal.GradleInternal;
import org.gradle.api.invocation.Gradle;

/**
 * @author Szczepan Faber, @date: 25.03.11
 */
public class ModelBuildingAdapter extends BuildAdapter {

    public static interface Builder {
        void buildAll(GradleInternal gradle);
        DefaultEclipseProject getProject();
    }

    public static interface Configurer {
        void configure(Project rootProject);
    }

    Builder builder;
    Configurer configurer;
    GradleInternal gradle;

    public ModelBuildingAdapter(Builder builder) {
        this.builder = builder;
    }

    public ModelBuildingAdapter configurer(Configurer configurer) {
        this.configurer = configurer;
        return this;
    }

    @Override
    public void projectsEvaluated(Gradle gradle) {
        this.gradle = (GradleInternal) gradle;
        try {
            Project root = gradle.getRootProject();
            if (configurer != null) {
                configurer.configure(root);
            }
            builder.buildAll(this.gradle);
        } finally {
            this.gradle = null;
        }
    }

    public DefaultEclipseProject getProject() {
        return builder.getProject();
    }
}
