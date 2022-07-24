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

package org.gradle.api.internal.tasks.properties.annotations;

import com.google.common.collect.ImmutableSet;
import org.gradle.api.internal.provider.PropertyInternal;
import org.gradle.internal.instantiation.PropertyRoleAnnotationHandler;
import org.gradle.internal.state.ModelObject;

import java.lang.annotation.Annotation;
import java.util.List;
import java.util.Set;

public class OutputPropertyRoleAnnotationHandler implements PropertyRoleAnnotationHandler {
    private final ImmutableSet<Class<? extends Annotation>> annotations;

    public OutputPropertyRoleAnnotationHandler(List<AbstractOutputPropertyAnnotationHandler> handlers) {
        ImmutableSet.Builder<Class<? extends Annotation>> builder = ImmutableSet.builderWithExpectedSize(handlers.size());
        for (AbstractOutputPropertyAnnotationHandler handler : handlers) {
            builder.add(handler.getAnnotationType());
        }
        this.annotations = builder.build();
    }

    @Override
    public Set<Class<? extends Annotation>> getAnnotationTypes() {
        return annotations;
    }

    @Override
    public void applyRoleTo(ModelObject owner, Object target) {
        if (target instanceof PropertyInternal) {
            ((PropertyInternal<?>) target).attachProducer(owner);
        }
    }
}
