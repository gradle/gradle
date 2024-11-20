/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.vcs.internal

import groovy.transform.SelfType
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.test.fixtures.file.TestFile

import static org.gradle.internal.hash.Hashing.hashString
import java.util.concurrent.AbstractExecutorService

@SelfType(AbstractIntegrationSpec)
trait SourceDependencies {
    TestFile checkoutDir(String repoName, String versionId, String repoId, TestFile baseDir=testDirectory) {
        def prefix = repoName.take(9)
        def hashedCommit = hashString("${repoId}-${versionId}").toCompactString()
        return baseDir.file(".gradle/vcs-1/${prefix}_${hashedCommit}/${repoName}")
    }
}
