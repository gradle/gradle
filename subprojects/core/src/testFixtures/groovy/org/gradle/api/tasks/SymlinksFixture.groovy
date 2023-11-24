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

package org.gradle.api.tasks

import org.gradle.api.file.LinksStrategy
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.UnitTestPreconditions

import java.nio.file.Files
import java.nio.file.Path
import java.text.Normalizer

import static java.nio.file.LinkOption.NOFOLLOW_LINKS
import static org.hamcrest.CoreMatchers.either
import static org.hamcrest.CoreMatchers.startsWith

@Requires(UnitTestPreconditions.Symlinks)
trait SymlinksFixture {

    void isValidSymlink(TestFile link, TestFile target) {
        assert link.exists()
        assert Files.isSymbolicLink(link.toPath())
        assert link.canonicalPath == target.canonicalPath
        haveSameContents(link, target)
    }

    void isBrokenSymlink(TestFile link, TestFile target) {
        Path linkPath = link.toPath()
        assert Files.exists(linkPath, NOFOLLOW_LINKS)
        assert Files.isSymbolicLink(linkPath)
        assert !Files.exists(Files.readSymbolicLink(linkPath))
    }

    void isNotASymlink(TestFile file) {
        assert file.exists()
        assert !Files.isSymbolicLink(file.toPath())
    }

    void isCopy(TestFile copy, TestFile original) {
        assert copy.exists()
        assert !Files.isSymbolicLink(copy.toPath())
        assert copy.canonicalPath != original.canonicalPath
        haveSameContents(copy, original)
        assert copy.permissions == original.permissions
    }

    void haveSameContents(TestFile one, TestFile other) {
        assert (one.isDirectory() & other.isDirectory()) | (one.isFile() & other.isFile())
        if (one.isDirectory() && other.isDirectory()) {
            def oneFiles = one.listFiles().collect { it.name }.toSet()
            def otherFiles = other.listFiles().collect { it.name }.toSet()
            assert oneFiles == otherFiles: "directories $one and $other are different"
        } else if (one.isFile() && other.isFile()) {
            assert one.text == other.text
        }
    }

    void haveSameTarget(TestFile one, TestFile other) {
        assert Files.isSymbolicLink(one.toPath()) & Files.isSymbolicLink(other.toPath())
        assert Files.readSymbolicLink(one.toPath()) == Files.readSymbolicLink(other.toPath())
    }

    Path relativePath(TestFile inputRoot, TestFile file) {
        return inputRoot.toPath().relativize(file.toPath())
    }

    Path relativePathToTarget(TestFile inputRoot, TestFile link) {
        return inputRoot.toPath().toRealPath().relativize(link.toPath().toRealPath())
    }

    String configureStrategy(LinksStrategy linksStrategy) {
        if (linksStrategy == null) {
            return ""
        }
        return "linksStrategy = LinksStrategy.$linksStrategy"
    }

    def assertHasCauseIgnoringUnicodeNormaization(String expectedMessage) {
        // ZIP can have different unicode normalization of file names
        def normalizedMessage = Normalizer.normalize(expectedMessage, Normalizer.Form.NFD)
        failure.assertThatCause(either(startsWith(expectedMessage)).or(startsWith(normalizedMessage)))
    }
}
