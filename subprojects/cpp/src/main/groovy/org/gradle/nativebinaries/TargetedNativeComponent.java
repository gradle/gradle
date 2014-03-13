/*
 * Copyright 2013 the original author or authors.
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
package org.gradle.nativebinaries;

import org.gradle.api.Incubating;
import org.gradle.internal.HasInternalProtocol;

/**
 * A native component that can be configured to target certain variant dimensions.
 */
@Incubating @HasInternalProtocol
public interface TargetedNativeComponent {

    /**
     * Specifies the names of one or more {@link Flavor}s that this component should be built for.
     */
    void targetFlavors(String... flavorSelectors);

    /**
     * Specifies the names of one or more {@link org.gradle.nativebinaries.platform.Platform}s that this component should be built for.
     */
    void targetPlatforms(String... platformSelectors);

    /**
     * Specifies the names of one or more {@link BuildType}s that this component should be built for.
     */
    void targetBuildTypes(String... platformSelectors);

}
