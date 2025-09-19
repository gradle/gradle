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
 * Provides a dynamic object that allows software features to be queried and applied to a target object as dynamic
 * methods.  The software features are looked up by name and if a matching feature is found, the feature is applied
 * to the target object and the configuration block applied to its public model object.
 */
abstract public class SoftwareFeaturesDynamicObject extends AbstractDynamicObject {
    private final DynamicObjectAware target;
    private final SoftwareFeatureSupportInternal.ProjectFeatureDefinitionContext context;

    @Inject
    public SoftwareFeaturesDynamicObject(DynamicObjectAware target, SoftwareFeatureSupportInternal.ProjectFeatureDefinitionContext context) {
        this.target = target;
        this.context = context;
    }

    @Override
    public String getDisplayName() {
        return "software features";
    }

    @Override
    public boolean hasMethod(String name, @Nullable Object... arguments) {
        return isSoftwareFeatureConfigureMethod(name, arguments);
    }

    private boolean isSoftwareFeatureConfigureMethod(String name, @Nullable Object[] arguments) {
        if (arguments == null || arguments.length != 1 || !(arguments[0] instanceof Closure)) {
            return false;
        }

        SoftwareFeatureImplementation<?, ?> feature = getSoftwareTypeRegistry().getSoftwareFeatureImplementations().get(name);
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
        if (isSoftwareFeatureConfigureMethod(name, arguments)) {
            Object softwareFeatureConfigurationModel = getSoftwareFeatureApplicator().applyFeatureTo(target, getSoftwareTypeRegistry().getSoftwareFeatureImplementations().get(name));
            return DynamicInvokeResult.found(ConfigureUtil.configure((Closure) arguments[0], softwareFeatureConfigurationModel));
        }
        return DynamicInvokeResult.notFound();
    }

    @Inject
    abstract protected SoftwareFeatureRegistry getSoftwareTypeRegistry();

    @Inject
    abstract protected SoftwareFeatureApplicator getSoftwareFeatureApplicator();

    public static final String CONTEXT_METHOD_NAME = "$.softwareFeatureContext";
}
