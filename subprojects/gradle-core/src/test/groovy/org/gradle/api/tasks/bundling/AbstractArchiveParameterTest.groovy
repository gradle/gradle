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

import static org.junit.Assert.*
import org.junit.Before
import org.junit.Test;

/**
 * @author Hans Dockter
 */
abstract class AbstractArchiveParameterTest {
    List expectedResourceCollections
    boolean expectedCreateIfEmpty
    File expectedDestinationDir
    String expectedArchiveName
    AntBuilder expectedAnt
    AntArchiveParameter archiveParameter

    abstract createAntArchiveParameter()

    @Before public void setUp()  {
        expectedResourceCollections = []
        expectedCreateIfEmpty = false
        expectedDestinationDir = 'somefile' as File
        expectedArchiveName = 'archivename'
        expectedAnt = new AntBuilder()
        archiveParameter = createAntArchiveParameter()
    }

    @Test public void testEquals() {
        AntArchiveParameter archiveParameter2 = createAntArchiveParameter()
        assert archiveParameter == archiveParameter2

        archiveParameter2.createIfEmpty = !archiveParameter2.createIfEmpty
        assert archiveParameter != archiveParameter2
    }

    @Test public void testHashCode() {
        AntArchiveParameter archiveParameter2 = createAntArchiveParameter()
        assert archiveParameter.hashCode() == archiveParameter2.hashCode()
    }

    @Test public void testAntArchiveParameter() {
        assert archiveParameter.resourceCollections.is(expectedResourceCollections)
        assert archiveParameter.createIfEmpty == expectedCreateIfEmpty
        assert archiveParameter.destinationDir.is(expectedDestinationDir)
        assert archiveParameter.archiveName == expectedArchiveName
        assert archiveParameter.ant.is(expectedAnt)
    }

    @Test public void testEmptyPolicy() {
        archiveParameter.createIfEmpty = false
        assertEquals(AbstractAntArchive.EMPTY_POLICY_SKIP, archiveParameter.emptyPolicy())
        archiveParameter.createIfEmpty = true
        assertEquals(AbstractAntArchive.EMPTY_POLICY_CREATE, archiveParameter.emptyPolicy())
    }
}
