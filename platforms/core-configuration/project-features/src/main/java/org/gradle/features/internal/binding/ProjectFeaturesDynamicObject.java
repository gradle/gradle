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

import groovy.lang.Closure;
import org.gradle.api.internal.DynamicObjectAware;
import org.gradle.internal.metaobject.AbstractDynamicObject;
import org.gradle.internal.metaobject.DynamicInvokeResult;
import org.gradle.util.internal.ConfigureUtil;
import org.jspecify.annotations.Nullable;

import javax.inject.Inject;
import java.util.Set;
import java.util.stream.Collectors;

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

        Set<ProjectFeatureImplementation<?, ?>> candidateFeatures = getProjectFeatureRegistry().getProjectFeatureImplementations().get(name);
        if (candidateFeatures == null) {
            return false;
        }

        return candidateFeatures.stream().anyMatch(feature ->
            TargetTypeInformationChecks.isValidBindingType(feature.getTargetDefinitionType(), target.getClass()));
    }

    @Override
    public DynamicInvokeResult tryInvokeMethod(String name, @Nullable Object... arguments) {
        if (name.equals(CONTEXT_METHOD_NAME) && arguments.length == 0) {
            return DynamicInvokeResult.found(context);
        }
        if (isFeatureConfigureMethod(name, arguments)) {
            Set<ProjectFeatureImplementation<?, ?>> candidateFeatures = getProjectFeatureRegistry().getProjectFeatureImplementations().get(name);
            if (candidateFeatures == null) {
                return DynamicInvokeResult.notFound();
            }
            Set<ProjectFeatureImplementation<?, ?>> matchingFeatures = candidateFeatures.stream().filter(feature ->
                TargetTypeInformationChecks.isValidBindingType(feature.getTargetDefinitionType(), target.getClass())
            ).collect(Collectors.toSet());
            if (matchingFeatures.isEmpty()) {
                return DynamicInvokeResult.notFound();
            }
            if (matchingFeatures.size() > 1) {
                // This should not be possible if the project feature bindings are correctly disambiguated at registration time
                throw new IllegalStateException(String.format("Multiple project features of name '%s' match target type '%s'. Cannot disambiguate.", name, target.getClass().getName()));
            }
            Object projectFeatureConfigurationModel = getProjectFeatureApplicator().applyFeatureTo(target, matchingFeatures.iterator().next());
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
