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

/**
 * The Gradle Enterprise plugin 3.4+ uses this to announce its presence and provide its service.
 *
 * It is obtained via the settings object's service registry for the root build only.
 */
public interface GradleEnterprisePluginCheckInService {

    /**
     * Registers the plugin with Gradle.
     *
     * @param pluginMetadata any information provided by the plugin to Gradle
     * @param serviceFactory a factory for a per-build-invocation service for the plugin
     */
    GradleEnterprisePluginCheckInResult checkIn(
        GradleEnterprisePluginMetadata pluginMetadata,
        GradleEnterprisePluginServiceFactory serviceFactory
    );

}
