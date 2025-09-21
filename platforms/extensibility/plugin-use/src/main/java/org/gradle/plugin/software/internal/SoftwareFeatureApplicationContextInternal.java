/*
 * Copyright 2025 the original author or authors.
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

import org.gradle.api.internal.DynamicObjectAware;
import org.gradle.api.internal.plugins.BuildModel;
import org.gradle.api.internal.plugins.HasBuildModel;
import org.gradle.api.internal.plugins.SoftwareFeatureApplicationContext;
import org.gradle.internal.Cast;

import javax.inject.Inject;

public interface SoftwareFeatureApplicationContextInternal extends SoftwareFeatureApplicationContext {

    @Inject
    SoftwareFeatureRegistry getSoftwareFeatureRegistry();

    @Inject
    SoftwareFeatureApplicator getSoftwareFeatureApplicator();

    @Override
    default <T extends HasBuildModel<V>, V extends BuildModel> V getBuildModel(T definition) {
        return Cast.uncheckedNonnullCast(SoftwareFeatureSupportInternal.getContext((DynamicObjectAware) definition).getBuildModel());
    }

    @Override
    default <T extends HasBuildModel<V>, V extends BuildModel> V registerBuildModel(T definition, Class<? extends V> implementationType) {
        SoftwareFeatureSupportInternal.ProjectFeatureDefinitionContext maybeContext = SoftwareFeatureSupportInternal.tryGetContext(definition);
        if (maybeContext != null) {
            throw new IllegalStateException("Definition object '" + definition + "' already has a registered build model '" + maybeContext.getBuildModel()
                + "'. Registering another build model for it is an error."
            );
        }

        V buildModel = getObjectFactory().newInstance(implementationType);
        SoftwareFeatureSupportInternal.attachDefinitionContext(definition, buildModel, getSoftwareFeatureApplicator(), getSoftwareFeatureRegistry(), getObjectFactory());

        return buildModel;
    }
}
