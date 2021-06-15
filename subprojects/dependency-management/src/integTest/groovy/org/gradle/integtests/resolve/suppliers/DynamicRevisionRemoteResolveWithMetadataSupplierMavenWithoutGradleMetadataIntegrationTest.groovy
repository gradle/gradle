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

package org.gradle.integtests.resolve.suppliers

import org.gradle.integtests.fixtures.GradleMetadataResolveRunner
import org.gradle.integtests.fixtures.RequiredFeature

@RequiredFeature(feature = GradleMetadataResolveRunner.REPOSITORY_TYPE, value = "ivy")
@RequiredFeature(feature = GradleMetadataResolveRunner.GRADLE_METADATA, value = "false")
class DynamicRevisionRemoteResolveWithMetadataSupplierMavenWithoutGradleMetadataIntegrationTest extends DynamicRevisionRemoteResolveWithMetadataSupplierIntegrationTest {

    // This class runs the tests from the super class with a specific combination of repository type and Gradle module metadata
}
