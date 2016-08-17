/*
 * Copyright 2012 the original author or authors.
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
package org.gradle.test.fixtures.maven

import org.gradle.test.fixtures.Module
import org.gradle.test.fixtures.file.TestFile

interface MavenModule extends Module {
    /**
     * Publishes the pom.xml plus main artifact, plus any additional artifacts for this module. Publishes only those artifacts whose content has
     * changed since the last call to {@code # publish ( )}.
     */
    MavenModule publish()

    /**
     * Publishes the pom.xml only
     */
    MavenModule publishPom()

    /**
     * Publishes the pom.xml plus main artifact, plus any additional artifacts for this module, with different content (and size) to any
     * previous publication.
     */
    MavenModule publishWithChangedContent()

    MavenModule withNonUniqueSnapshots()

    MavenModule parent(String group, String artifactId, String version)

    MavenModule dependsOnModules(String... dependencyArtifactIds)

    MavenModule dependsOn(Module module)

    MavenModule dependsOn(Map<String, ?> attributes, Module module)

    MavenModule dependsOn(String group, String artifactId, String version)

    MavenModule dependsOn(String group, String artifactId, String version, String type, String scope, String classifier)

    MavenModule hasPackaging(String packaging)

    /**
     * Sets the type of the main artifact for this module.
     */
    MavenModule hasType(String type)

    /**
     * Asserts exactly pom and jar published, along with checksums.
     */
    void assertPublishedAsJavaModule()

    String getPublishArtifactVersion()

    String getGroupId()

    String getArtifactId()

    String getVersion()

    TestFile getPomFile()

    TestFile getArtifactFile()

    TestFile getMetaDataFile()

    MavenPom getParsedPom()

    MavenMetaData getRootMetaData()
}
