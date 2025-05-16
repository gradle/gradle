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

package gradlebuild.identity.extension

import org.gradle.api.provider.Property
import org.gradle.util.GradleVersion

/**
 * Describes defining properties of a Gradle module.
 */
interface ModuleIdentityExtension {

    /**
     * The name of this Gradle module as it should appear in output files and published metadata.
     */
    val baseName: Property<String>

    /**
     * Declares whether this module is published to an external repository.
     */
    val published: Property<Boolean>

    // TODO: These are build-wide properties, we should not configure them per module.
    val version: Property<GradleVersion>
    val buildTimestamp: Property<String>
    val snapshot: Property<Boolean>
    val promotionBuild: Property<Boolean>
    val releasedVersions: Property<ReleasedVersionsDetails>

}
