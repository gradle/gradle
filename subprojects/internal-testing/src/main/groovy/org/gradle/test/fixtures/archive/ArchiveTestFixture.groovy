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

package org.gradle.test.fixtures.archive

import com.google.common.collect.ArrayListMultimap
import com.google.common.collect.LinkedListMultimap
import com.google.common.collect.ListMultimap
import org.gradle.util.CollectionUtils
import org.hamcrest.Matcher

import static org.hamcrest.Matchers.equalTo
import static org.hamcrest.Matchers.hasItem
import static org.junit.Assert.assertEquals
import static org.junit.Assert.assertThat

class ArchiveTestFixture {
    private final ListMultimap<String, String> filesByRelativePath = LinkedListMultimap.create()
    private final ListMultimap<String, Integer> fileModesByRelativePath = ArrayListMultimap.create()

    protected void add(String relativePath, String content) {
        filesByRelativePath.put(relativePath, content)
    }

    protected void addMode(String relativePath, int mode) {
        fileModesByRelativePath.put(relativePath, mode & 0777)
    }

    def assertFileMode(String relativePath, int fileMode) {
        List<Integer> modes = fileModesByRelativePath.get(relativePath)
        assert modes.size() == 1
        assertThat(modes.get(0), equalTo(fileMode))
        this
    }

    def assertContainsFile(String relativePath) {
        assert filesByRelativePath.keySet().contains(relativePath)
        this
    }

    def assertNotContainsFile(String relativePath) {
        assert !filesByRelativePath.keySet().contains(relativePath)
        this
    }

    def assertContainsFile(String relativePath, int occurrences) {
        assertContainsFile(relativePath)
        def actualOccurrences = filesByRelativePath.get(relativePath).size()
        def failureMessage = String.format("Incorrect count for file '%s': expected %s, got %s", relativePath, occurrences, actualOccurrences)
        assertEquals(failureMessage, occurrences, actualOccurrences)
        this
    }

    String content(String relativePath) {
        List<String> files = filesByRelativePath.get(relativePath)
        assert files.size() == 1
        files.get(0)
    }

    Integer countFiles(String relativePath) {
        filesByRelativePath.get(relativePath).size()
    }

    def hasDescendants(String... relativePaths) {
        assertThat(filesByRelativePath.keySet(), equalTo(relativePaths as Set))
        def expectedCounts = ArrayListMultimap.create()
        for (String fileName : relativePaths) {
            expectedCounts.put(fileName, fileName)
        }
        for (String fileName : relativePaths) {
            assertEquals(expectedCounts.get(fileName).size(), filesByRelativePath.get(fileName).size())
        }
        this
    }

    def hasDescendantsInOrder(String... relativePaths) {
        def expectedOrder = CollectionUtils.toList(relativePaths)
        def actualOrder = CollectionUtils.toList(filesByRelativePath.keySet())
        assertEquals(actualOrder, expectedOrder)
        this
    }

    def containsDescendants(String... relativePaths) {
        for (String path : relativePaths) {
            assertContainsFile(path)
        }
        this
    }

    def doesNotContainDescendants(String... relativePaths) {
        for (String path : relativePaths) {
            assertNotContainsFile(path)
        }
        this
    }

    /**
     * Asserts that there is exactly one file present with the given path, and that this file has the given content.
     */
    def assertFileContent(String relativePath, String fileContent) {
        assertFileContent(relativePath, equalTo(fileContent))
    }

    def assertFileContent(String relativePath, Matcher contentMatcher) {
        assertThat(content(relativePath), contentMatcher)
        this
    }

    /**
     * Asserts that there is a file present with the given path and content.
     */
    def assertFilePresent(String relativePath, String fileContent) {
        assertThat(filesByRelativePath.get(relativePath), hasItem(fileContent))
        this
    }
}
