/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.internal.enterprise;

import java.io.Serializable;

/**
 * Creates a per-build-invocation plugin service.
 *
 * Gradle is responsible for creating the service via this factory for each build invocation.
 */
public interface GradleEnterprisePluginServiceFactory extends Serializable {

    /**
     * Creates a per-build-invocation plugin service.
     *
     * Should be called at-most-once for the build tree scope.
     *
     * @param config information about the plugin conveyed by Gradle
     * @param requiredServices infrastructure like services required by the plugin
     * @param buildState state particular to the build invocation
     * @return the plugin service
     */
    GradleEnterprisePluginService create(
        GradleEnterprisePluginConfig config,
        GradleEnterprisePluginRequiredServices requiredServices,
        GradleEnterprisePluginBuildState buildState
    );

}
