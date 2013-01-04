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

import org.gradle.test.fixtures.file.TestFile

interface MavenModule {
    /**
     * Publishes the pom.xml plus main artifact, plus any additional artifacts for this module.
     */
    MavenModule publish()

    /**
     * Publishes the pom.xml plus main artifact, plus any additional artifacts for this module, with changed content to any
     * previous publication.
     */
    MavenModule publishWithChangedContent()

    MavenModule withNonUniqueSnapshots()

    MavenModule parent(String group, String artifactId, String version)

    MavenModule dependsOn(String group, String artifactId, String version)

    MavenModule hasPackaging(String packaging)

    TestFile getPomFile()

    TestFile getArtifactFile()

    TestFile getMetaDataFile()

    MavenPom getParsedPom()

    MavenMetaData getRootMetaData()
}