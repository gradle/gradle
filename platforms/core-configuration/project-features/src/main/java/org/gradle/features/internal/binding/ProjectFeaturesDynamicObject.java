/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.features.internal.binding;

import org.gradle.internal.metaobject.AbstractDynamicObject;
import org.gradle.internal.metaobject.DynamicInvokeResult;
import org.jspecify.annotations.Nullable;

import javax.inject.Inject;

/**
 * Provides a dynamic object that stores and retrieves the {@link ProjectFeatureSupportInternal.ProjectFeatureDefinitionContext}
 * for a target object. This context is used internally to access feature metadata during feature application.
 */
abstract public class ProjectFeaturesDynamicObject extends AbstractDynamicObject {
    private final ProjectFeatureSupportInternal.ProjectFeatureDefinitionContext context;

    @Inject
    public ProjectFeaturesDynamicObject(ProjectFeatureSupportInternal.ProjectFeatureDefinitionContext context) {
        this.context = context;
    }

    @Override
    public String getDisplayName() {
        return "project features";
    }

    @Override
    public DynamicInvokeResult tryInvokeMethod(String name, @Nullable Object... arguments) {
        if (name.equals(CONTEXT_METHOD_NAME) && arguments.length == 0) {
            return DynamicInvokeResult.found(context);
        }
        return DynamicInvokeResult.notFound();
    }

    public static final String CONTEXT_METHOD_NAME = "$.projectFeatureContext";
}
