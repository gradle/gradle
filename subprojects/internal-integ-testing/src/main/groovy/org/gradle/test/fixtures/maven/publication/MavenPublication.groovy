/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.test.fixtures.maven.publication

import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.maven.IntegrityChecker
import org.gradle.test.fixtures.maven.ModuleDescriptor

abstract class MavenPublication implements IntegrityChecker {
    public final static String MAVEN_METADATA_FILE = "maven-metadata.xml"
    TestFile moduleDir
    TestFile baseDir
    ModuleDescriptor moduleDescriptor
    MavenItem mavenMetaDataItem
    RootMetaData rootMetadata
    List<PublishedModule> versions = []

    abstract List<PublishedModule> readVersions()
    abstract boolean assertJavaPublicationWithSourceAndJavadoc()
    abstract boolean assertJavaPublication()

    MavenPublication(TestFile baseDir, ModuleDescriptor moduleDescriptor) {
        this.baseDir = baseDir
        this.moduleDescriptor = moduleDescriptor
        this.moduleDir = baseDir.file(moduleDescriptor.rootDirectory())
        this.mavenMetaDataItem = new MavenItem(moduleDir, MAVEN_METADATA_FILE)
        this.rootMetadata = new RootMetaData(mavenMetaDataItem)
        this.versions = readVersions()
    }

    PublishedModule getVersion(String version) {
        versions.find { it.version == version }
    }
}