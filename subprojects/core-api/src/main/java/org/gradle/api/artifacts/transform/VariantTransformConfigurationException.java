/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.api.artifacts.transform;

import org.gradle.api.GradleException;
import org.gradle.internal.deprecation.DeprecationLogger;
import org.gradle.internal.exceptions.Contextual;

/**
 * An exception to report a problem during a transform execution.
 * <p>
 * This exception is deprecated and will be removed in a future version of Gradle.  There
 * is no public use case for this.  Gradle should use {@code org.gradle.api.internal.artifacts.transform.VariantTransformConfigurationException}
 * instead.
 *
 * @since 3.5
 */
@Contextual
@Deprecated
public class VariantTransformConfigurationException extends GradleException {
    public VariantTransformConfigurationException(String message, Throwable cause) {
        super(message, cause);
        announceDeprecation();
    }

    public VariantTransformConfigurationException(String message) {
        super(message);
        announceDeprecation();
    }

    private static void announceDeprecation() {
        DeprecationLogger.deprecateType(VariantTransformConfigurationException.class)
            .willBeRemovedInGradle9()
            .withUpgradeGuideSection(9, "deprecated_transform_configuration_exception")
            .nagUser();
    }
}
