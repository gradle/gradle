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
package org.gradle.test.fixtures.server.http

import javax.servlet.http.HttpServletResponse

interface HttpPluginRepository {

    void expectPluginMarkerMissing(String pluginId, String pluginVersion)

    void expectPluginMarkerBroken(String pluginId, String pluginVersion)

    void expectPluginMarkerQuery(String pluginId, String pluginVersion,
                                 @DelegatesTo(value = HttpServletResponse, strategy = Closure.DELEGATE_FIRST) Closure<?> markerQueryConfigurer)

    void expectPluginResolution(String pluginId, String pluginVersion, String group, String artifactId, String version)

    void expectCachedPluginResolution(String pluginId, String pluginVersion, String group, String artifactId, String version)
}
