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

import org.gradle.api.plugins.ExtensionAware;
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;

/**
 * Encapsulates the work of applying a software feature to a target.
 *
 * @since 8.12
 */
@ServiceScope(Scope.Project.class)
public interface SoftwareFeatureApplicator {
    /**
     * Applies the given software feature to the target, registering the public model object as an extension of the target and
     * returning it.  This method can be called multiple times, but will only apply the feature once.
     *
     * @param target - the receiver object to apply the feature to
     * @param softwareFeature - the feature to apply
     * @return the public model object for the feature
     * @param <T> the type of the public model object for the feature
     * @since 8.12
     */
    <T> T applyFeatureTo(ExtensionAware target, SoftwareTypeImplementation<T> softwareFeature);
}
