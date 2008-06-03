/*
 * Copyright 2007 the original author or authors.
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
 
package org.gradle.api.tasks.bundling

import org.gradle.api.tasks.util.FileSet

/**
 * @author Hans Dockter
 */
class AntMetaArchiveParameterTest extends AbstractArchiveParameterTest {
    GradleManifest expectedGradleManifest
    List expectedMetaInfFileSets
    String expectedFileSetManifest

    public void setUp() {
        expectedGradleManifest = new GradleManifest()
        expectedMetaInfFileSets = [new FileSet('somedir')]
        expectedFileSetManifest = 'skip'
        super.setUp()
    }

    public createAntArchiveParameter() {
        new AntMetaArchiveParameter(expectedResourceCollections, expectedMergeFileSets, expectedMergeGroupFileSets, expectedFileSetManifest,
                expectedCreateIfEmpty,
                expectedDestinationDir, expectedArchiveName, expectedGradleManifest, expectedMetaInfFileSets, expectedAnt)
    }

    public void testAntArchiveParameter() {
        super.testAntArchiveParameter()
        AntMetaArchiveParameter metaArchiveParameter = archiveParameter
        assert metaArchiveParameter.gradleManifest.is(expectedGradleManifest)
        assert metaArchiveParameter.metaInfFileSets.is(expectedMetaInfFileSets)
        assertEquals(expectedFileSetManifest, metaArchiveParameter.fileSetManifest)
    }


    
}
