/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.internal.buildtree;

import org.gradle.api.internal.StartParameterInternal;
import org.gradle.internal.service.ServiceRegistration;
import org.gradle.internal.service.scopes.Scopes;
import org.gradle.internal.service.scopes.ServiceScope;

@ServiceScope(Scopes.BuildSession.class)
public interface BuildTreeModelControllerServices {
    /**
     * Creates a {@link Supplier} that will contribute services required for the model of a build tree with the given parameters.
     *
     * <p>Contributes the following services:</p>
     * <ul>
     *     <li>{@link org.gradle.api.internal.BuildType}</li>
     *     <li>{@link BuildModelParameters}</li>
     *     <li>{@link BuildActionModelRequirements}</li>
     * </ul>
     */
    Supplier servicesForBuildTree(BuildActionModelRequirements actionModelRequirements);

    /**
     * Creates a {@link Supplier} that will contribute the services required for the model of a nested build tree with the given parameters.
     *
     * <p>Contributes the same services as {@link #servicesForBuildTree(BuildActionModelRequirements)}.</p>
     */
    Supplier servicesForNestedBuildTree(StartParameterInternal startParameter);

    interface Supplier {
        void applyServicesTo(ServiceRegistration registration);
    }
}
