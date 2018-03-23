/*
 * Copyright 2018 the original author or authors.
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
package org.gradle.plugins.publish

import org.junit.Assert
import org.junit.Test


class ArtifactPatternTest {
    @Test
    fun `given snapshot library and typical other values it returns a correct URL`() {
        // given:
        val pattern = createArtifactPattern(true, "foo.bar", "someArtifact")

        // then:
        Assert.assertEquals(
            "$GRADLE_REPO/libs-snapshots-local/foo/bar/someArtifact/[revision]/[artifact]-[revision](-[classifier]).[ext]",
            pattern)
    }

    @Test
    fun `given release library it returns a correct URL`() {
        // given:
        val pattern = createArtifactPattern(false, "foo.bar", "someArtifact")

        // then:
        Assert.assertEquals(
            "$GRADLE_REPO/libs-releases-local/foo/bar/someArtifact/[revision]/[artifact]-[revision](-[classifier]).[ext]",
            pattern)
    }

    @Test
    fun `given single group name it returns a correct URL`() {
        // given:
        val pattern = createArtifactPattern(false, "foo", "someArtifact")

        // then:
        Assert.assertEquals(
            "$GRADLE_REPO/libs-releases-local/foo/someArtifact/[revision]/[artifact]-[revision](-[classifier]).[ext]",
            pattern)
    }

    @Test(expected = AssertionError::class)
    fun `given empty group name it returns an exception`() {
        // given:
        createArtifactPattern(false, "", "someArtifact")
    }

    @Test(expected = AssertionError::class)
    fun `given empty artifact name it returns an exception`() {
        // given:
        createArtifactPattern(false, "foo", "")
    }
}
