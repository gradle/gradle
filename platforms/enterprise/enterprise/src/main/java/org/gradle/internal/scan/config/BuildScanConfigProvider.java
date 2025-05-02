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

package org.gradle.internal.scan.config;

import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;

/**
 * A check-in service used by the Gradle Enterprise plugin versions until 3.4, none of which are supported anymore.
 * <p>
 * We keep this service, because for the plugin versions 3.0+ we can gracefully avoid plugin application and report an unsupported message.
 * <p>
 * Obtained via the root project's gradle object's service registry.
 *
 * @since 4.0
 */
@ServiceScope(Scope.Build.class)
public interface BuildScanConfigProvider {

    /**
     * Invoked by the scan plugin to "collect" the configuration.
     *
     * Will only be called once per build.
     */
    BuildScanConfig collect(BuildScanPluginMetadata pluginMetadata);

}
