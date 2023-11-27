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
package org.gradle.api.internal.file.copy


import org.gradle.api.file.RelativePath
import org.gradle.api.internal.file.CopyActionProcessingStreamAction
import org.gradle.api.tasks.WorkResults
import spock.lang.Specification

import static org.gradle.api.internal.file.TestFiles.fileSystem
import static org.gradle.api.internal.file.copy.CopyActionExecuterUtil.visit

class NormalizingCopyActionDecoratorTest extends Specification {
    def delegateAction = Mock(CopyActionProcessingStreamAction)
    def delegate = { stream ->
        stream.process(delegateAction)
        return WorkResults.didWork(true)
    } as CopyAction
    def decorator = new NormalizingCopyActionDecorator(delegate, fileSystem())

    def doesNotVisitADirectoryWhichHasBeenVisitedBefore() {
        given:
        final FileCopyDetailsInternal details = file("dir", true, true)
        final FileCopyDetailsInternal file = file("dir/file", false, true)

        when:
        visit(decorator, details, file, details)

        then:
        1 * delegateAction.processFile(details)
        1 * delegateAction.processFile(file)
    }

    def visitsDirectoryAncestorsWhichHaveNotBeenVisited() {
        given:
        final FileCopyDetailsInternal dir1 = file("a/b/c", true, true)
        final FileCopyDetailsInternal file1 = file("a/b/c/file", false, true)
        final FileCopyDetailsInternal dir2 = file("a/b/d/e", true, true)
        final FileCopyDetailsInternal file2 = file("a/b/d/e/file", false, true)

        when:
        decorator.execute(new CopyActionProcessingStream() {
            void process(CopyActionProcessingStreamAction action) {
                action.processFile(dir1)
                action.processFile(file1)

                action.processFile(dir2)
                action.processFile(file2)
            }
        })

        then:
        1 * delegateAction.processFile({ it.path == 'a'})
        1 * delegateAction.processFile({ it.path == 'a/b'})
        1 * delegateAction.processFile(dir1)
        1 * delegateAction.processFile(file1)
        1 * delegateAction.processFile({ it.path == 'a/b/d'})
        1 * delegateAction.processFile(dir2)
        1 * delegateAction.processFile(file2)
    }

    def visitsFileAncestorsWhichHaveNotBeenVisited() {
        final FileCopyDetailsInternal details = file("a/b/c", false, true)

        when:
        visit(decorator, details)

        then:
        1 * delegateAction.processFile({ it.path == 'a'})
        1 * delegateAction.processFile({ it.path == 'a/b'})
        1 * delegateAction.processFile(details)
    }

    def visitsAnEmptyDirectoryIfCorrespondingOptionIsOn() {
        final FileCopyDetailsInternal dir = file("dir", true, true)

        when:
        visit(decorator, dir)

        then:
        1 * delegateAction.processFile(dir)
    }

    def doesNotVisitAnEmptyDirectoryIfCorrespondingOptionIsOff() {
        final FileCopyDetailsInternal dir = file("dir", true, false)

        when:
        visit(decorator, dir)

        then:
        0 * delegateAction._
    }

    private FileCopyDetailsInternal file(final String path, final boolean isDir, final boolean includeEmptyDirs) {
        return Stub(FileCopyDetailsInternal) {
            getRelativePath() >> RelativePath.parse(false, path)
            isDirectory() >> isDir
            getSpecResolver() >> Stub(CopySpecResolver) {
                getIncludeEmptyDirs() >> includeEmptyDirs
            }
        }
    }
}
