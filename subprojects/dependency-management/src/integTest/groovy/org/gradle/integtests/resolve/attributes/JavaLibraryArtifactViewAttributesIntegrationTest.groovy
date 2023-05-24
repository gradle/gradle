/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.integtests.resolve.attributes

import org.gradle.api.JavaVersion


/**
 * Tests for [org.gradle.api.artifacts.ArtifactView ArtifactView] that ensure it uses the "live" attributes
 * of the configuration that created it to select files and artifacts.
 *
 * As the necessary attributes are not present on the consumer's compileClasspath configuration until the
 * beforeLocking callback method is called, the previous incorrect behavior of the ArtifactView was to select without
 * considering these attributes.  This has been corrected to use a "live" view
 * of the attributes, to ensure that late changes to the attributes are reflected in the selection.
 *
 * These tests use the `java-library` plugin and should resolve the secondary "classes" variant of the producer
 * and obtain the "main" directory as the only file, instead of the standard jar file variant, which was the legacy
 * behavior.
 */
class JavaLibraryArtifactViewAttributesIntegrationTest extends AbstractArtifactViewAttributesIntegrationTest {
    @Override
    List<String> getExpectedFileNames() {
        return ["main"]
    }

    @Override
    List<String> getExpectedAttributes() {
        return ['org.gradle.category = library',
                'org.gradle.usage = java-api',
                'org.gradle.dependency.bundling = external',
                'org.gradle.jvm.environment = standard-jvm',
                'org.gradle.libraryelements = classes',
                "org.gradle.jvm.version = ${JavaVersion.current().majorVersion}"]
    }

    @Override
    String getTestedClasspathName() {
        return "compileClasspath"
    }

    def setup() {
        file("producer/build.gradle") << """
            plugins {
                id 'java-library'
            }
        """

        buildFile << """
            plugins {
                id 'java-library'
            }

            dependencies {
                api project(':producer')
            }
        """
    }
}
