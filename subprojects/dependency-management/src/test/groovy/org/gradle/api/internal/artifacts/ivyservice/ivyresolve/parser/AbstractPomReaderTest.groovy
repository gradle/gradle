/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.api.internal.artifacts.ivyservice.ivyresolve.parser

import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.parser.data.MavenDependencyKey
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.parser.data.PomDependencyMgt
import org.gradle.api.internal.file.TestFiles
import org.gradle.internal.resource.local.LocalFileStandInExternalResource
import org.gradle.internal.resource.local.LocallyAvailableExternalResource
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification

abstract class AbstractPomReaderTest extends Specification {
    @Rule public final TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider(getClass())
    PomReader pomReader
    TestFile pomFile
    LocallyAvailableExternalResource locallyAvailableExternalResource

    def setup() {
        pomFile = tmpDir.file('pom.xml')
        pomFile.createFile()
        locallyAvailableExternalResource = new LocalFileStandInExternalResource(pomFile, TestFiles.fileSystem())
    }

    protected void assertResolvedPomDependency(MavenDependencyKey key, String version) {
        assert pomReader.dependencies.containsKey(key)
        PomReader.PomDependencyData dependency = pomReader.dependencies[key]
        assertPomDependencyValues(key, version, dependency)
    }

    protected void assertResolvedPomDependencyManagement(MavenDependencyKey key, String version) {
        assert pomReader.dependencyMgt.containsKey(key)
        PomDependencyMgt dependency = pomReader.dependencyMgt[key]
        assertPomDependencyValues(key, version, dependency)
    }

    protected void assertPomDependencyValues(MavenDependencyKey key, String version, PomDependencyMgt dependency) {
        assert dependency.groupId == key.groupId
        assert dependency.artifactId == key.artifactId
        assert dependency.type == key.type
        assert dependency.classifier == key.classifier
        assert dependency.version == version
    }

    protected PomReader createPomReader(String pomFileName, String pomDefinition) {
        TestFile pomFile = tmpDir.file(pomFileName)
        pomFile.createFile()
        pomFile << pomDefinition
        LocallyAvailableExternalResource locallyAvailableExternalResource = new LocalFileStandInExternalResource(pomFile, TestFiles.fileSystem())
        return new PomReader(locallyAvailableExternalResource, moduleIdentifierFactory)
    }
}
