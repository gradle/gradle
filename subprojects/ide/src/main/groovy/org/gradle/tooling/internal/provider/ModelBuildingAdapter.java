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
import org.gradle.api.internal.GradleInternal;
import org.gradle.api.invocation.Gradle;

/**
 * @author Szczepan Faber, @date: 25.03.11
 */
public class ModelBuildingAdapter extends BuildAdapter {

    private final ModelBuilder builder;
    private Object eclipseProject;

    public ModelBuildingAdapter(ModelBuilder builder) {
        this.builder = builder;
    }

    @Override
    public void projectsEvaluated(Gradle gradle) {
        eclipseProject = builder.buildAll((GradleInternal) gradle);
    }

    public Object getProject() {
        return eclipseProject;
    }
}
