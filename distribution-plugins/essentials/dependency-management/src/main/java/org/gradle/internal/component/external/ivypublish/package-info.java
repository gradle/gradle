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

/**
 * Types used for legacy publishing to Ivy, using `Upload` task.
 * We first convert a `Configuration` into these types, and then use the
 * {@link org.gradle.internal.component.external.ivypublish.DefaultIvyModuleDescriptorWriter}
 * to generate the Ivy module descriptor.
 * The {@link org.gradle.api.internal.artifacts.ModuleVersionPublisher#publish(org.gradle.internal.component.external.ivypublish.IvyModulePublishMetadata)}
 * methods are then used to upload the actual files.
 *
 * Note that this is all slated for replacement by the `ivy-publish` and `maven-publish` plugins.
 * Many of the types in this package are misnamed, since they apply equally to both Maven and Ivy legacy publishing.
 */
@NonNullApi
package org.gradle.internal.component.external.ivypublish;

import org.gradle.api.NonNullApi;
