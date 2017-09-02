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

/**
 * Can be used to determine if the build scan plugin has been applied.
 *
 * This is available as a build scoped service.
 * We use a dedicated service for detecting the plugin application since
 *  - we want to have a deep integration
 *  - we want to have only one way how to ask if the plugin has been applied
 */
public interface BuildScanPluginApplied {
    boolean isBuildScanPluginApplied();
}
