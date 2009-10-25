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
import org.junit.Before
import org.junit.Test
import static org.junit.Assert.*
import org.gradle.api.internal.file.FileResolver;

/**
 * @author Hans Dockter
 */
class AntMetaArchiveParameterTest extends AbstractArchiveParameterTest {
    FileResolver resolver = [resolve: {it as File}] as FileResolver
    GradleManifest expectedGradleManifest
    List expectedMetaInfFileSets
    String expectedFileSetManifest

    @Before public void setUp()  {
        expectedGradleManifest = new GradleManifest()
        expectedMetaInfFileSets = [new FileSet(new File('somedir'), resolver)]
        expectedFileSetManifest = 'skip'
        super.setUp()
    }

    public createAntArchiveParameter() {
        new AntMetaArchiveParameter(expectedResourceCollections, expectedFileSetManifest, expectedCreateIfEmpty,
                expectedDestinationDir, expectedArchiveName, expectedGradleManifest, expectedMetaInfFileSets, expectedAnt)
    }

    @Test public void testAntArchiveParameter() {
        super.testAntArchiveParameter()
        AntMetaArchiveParameter metaArchiveParameter = archiveParameter
        assert metaArchiveParameter.gradleManifest.is(expectedGradleManifest)
        assert metaArchiveParameter.metaInfFileSets.is(expectedMetaInfFileSets)
        assertEquals(expectedFileSetManifest, metaArchiveParameter.fileSetManifest)
    }


    
}
