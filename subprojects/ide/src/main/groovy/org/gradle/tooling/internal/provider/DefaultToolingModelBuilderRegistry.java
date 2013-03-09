/*
 * Copyright 2012 the original author or authors.
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

import java.util.List;

import static java.util.Arrays.asList;

public class DefaultToolingModelBuilderRegistry implements ToolingModelBuilderRegistry {
    public BuildsModel getBuilder(Class<?> modelType) {
        List<? extends BuildsModel> modelBuilders = asList(
                new NullResultBuilder(),
                new EclipseModelBuilder(),
                new IdeaModelBuilder(),
                new GradleProjectBuilder(),
                new BasicIdeaModelBuilder(),
                new ProjectOutcomesModelBuilder());

        for (BuildsModel builder : modelBuilders) {
            if (builder.canBuild(modelType)) {
                return builder;
            }
        }

        throw new UnsupportedOperationException(String.format("I don't know how to build a model of type '%s'.", modelType.getSimpleName()));
    }
}
