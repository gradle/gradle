/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.plugin.software.internal;

import groovy.lang.Closure;
import org.gradle.api.internal.DynamicObjectAware;
import org.gradle.internal.metaobject.AbstractDynamicObject;
import org.gradle.internal.metaobject.DynamicInvokeResult;
import org.gradle.util.internal.ConfigureUtil;
import org.jspecify.annotations.Nullable;

import javax.inject.Inject;

/**
 * Provides a dynamic object that allows project features to be queried and applied to a target object as dynamic
 * methods.  The project features are looked up by name and if a matching feature is found, the feature is applied
 * to the target object and the configuration block applied to its public model object.
 */
abstract public class ProjectFeaturesDynamicObject extends AbstractDynamicObject {
    private final DynamicObjectAware target;
    private final ProjectFeatureSupportInternal.ProjectFeatureDefinitionContext context;

    @Inject
    public ProjectFeaturesDynamicObject(DynamicObjectAware target, ProjectFeatureSupportInternal.ProjectFeatureDefinitionContext context) {
        this.target = target;
        this.context = context;
    }

    @Override
    public String getDisplayName() {
        return "project features";
    }

    @Override
    public boolean hasMethod(String name, @Nullable Object... arguments) {
        return isFeatureConfigureMethod(name, arguments);
    }

    private boolean isFeatureConfigureMethod(String name, @Nullable Object[] arguments) {
        if (arguments == null || arguments.length != 1 || !(arguments[0] instanceof Closure)) {
            return false;
        }

        ProjectFeatureImplementation<?, ?> feature = getProjectFeatureRegistry().getProjectFeatureImplementations().get(name);
        if (feature == null) {
            return false;
        }

        return TargetTypeInformationChecks.isValidBindingType(feature.getTargetDefinitionType(), target.getClass());
    }

    @Override
    public DynamicInvokeResult tryInvokeMethod(String name, @Nullable Object... arguments) {
        if (name.equals(CONTEXT_METHOD_NAME) && arguments.length == 0) {
            return DynamicInvokeResult.found(context);
        }
        if (isFeatureConfigureMethod(name, arguments)) {
            Object projectFeatureConfigurationModel = getProjectFeatureApplicator().applyFeatureTo(target, getProjectFeatureRegistry().getProjectFeatureImplementations().get(name));
            return DynamicInvokeResult.found(ConfigureUtil.configure((Closure) arguments[0], projectFeatureConfigurationModel));
        }
        return DynamicInvokeResult.notFound();
    }

    @Inject
    abstract protected ProjectFeatureDeclarations getProjectFeatureRegistry();

    @Inject
    abstract protected ProjectFeatureApplicator getProjectFeatureApplicator();

    public static final String CONTEXT_METHOD_NAME = "$.projectFeatureContext";
}
