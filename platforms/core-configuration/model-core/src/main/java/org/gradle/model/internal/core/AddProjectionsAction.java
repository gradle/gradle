/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.model.internal.core;

import org.gradle.model.internal.core.rule.describe.ModelRuleDescriptor;

import java.util.Arrays;
import java.util.List;

public class AddProjectionsAction<T> extends AbstractModelAction<T> {
    private final Iterable<ModelProjection> projections;

    private AddProjectionsAction(ModelReference<T> subject, ModelRuleDescriptor descriptor, Iterable<ModelProjection> projections) {
        super(subject, descriptor);
        this.projections = projections;
    }

    public static <T> AddProjectionsAction<T> of(ModelReference<T> subject, ModelRuleDescriptor descriptor, ModelProjection... projections) {
        return of(subject, descriptor, Arrays.asList(projections));
    }

    public static <T> AddProjectionsAction<T> of(ModelReference<T> subject, ModelRuleDescriptor descriptor, Iterable<ModelProjection> projections) {
        return new AddProjectionsAction<T>(subject, descriptor, projections);
    }

    @Override
    public void execute(MutableModelNode modelNode, List<ModelView<?>> inputs) {
        for (ModelProjection projection : projections) {
            modelNode.addProjection(projection);
        }
    }
}
