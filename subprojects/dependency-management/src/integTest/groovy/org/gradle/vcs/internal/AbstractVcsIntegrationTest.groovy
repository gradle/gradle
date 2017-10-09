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

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.internal.hash.HashUtil
import org.gradle.test.fixtures.file.TestFile

abstract class AbstractVcsIntegrationTest extends AbstractIntegrationSpec {
    def setup() {
        buildFile << """
            apply plugin: 'java'
            group = 'org.gradle'
            version = '2.0'
            
            dependencies {
                compile "org.test:dep:latest.integration"
            }
        """
        file("src/main/java/Main.java") << """
            public class Main {
                Dep dep = null;
            }
        """
        buildTestFixture.withBuildInSubDir()
        singleProjectBuild("dep") {
            buildFile << """
                apply plugin: 'java'
            """
            file("src/main/java/Dep.java") << "public class Dep {}"
        }
    }

    TestFile checkoutDir(String repoName, String versionId, String repoId) {
        def hashedRepo = HashUtil.createCompactMD5(repoId)
        file(".gradle/vcsWorkingDirs/${hashedRepo}/${versionId}/${repoName}")
    }
}
