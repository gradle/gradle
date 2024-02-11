/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.platform.base.internal;

import org.gradle.platform.base.PlatformAwareComponentSpec;

import java.util.List;

/**
 * Defines Platform specific operations for ComponentSpecs
 */
public interface PlatformAwareComponentSpecInternal extends PlatformAwareComponentSpec, HasIntermediateOutputsComponentSpec {
    /**
     * Get the names of the targeted platforms that this component should be built for.
     *
     * @return the list of targeted platforms, may be empty but never null.
     */
    List<PlatformRequirement> getTargetPlatforms();
}
