/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.integtests.fixtures

import groovy.transform.SelfType
import org.gradle.integtests.fixtures.executer.GradleExecuter
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Before

@SelfType(AbstractIntegrationSpec)
trait LocalBuildCacheFixture {
    private TestFile cacheDir

    abstract TestNameTestDirectoryProvider getTemporaryFolder()
    abstract GradleExecuter getExecuter()

    @Before
    void setupCacheDirectory() {
        // Make sure cache dir is empty for every test execution
        cacheDir = temporaryFolder.file("cache-dir").deleteDir().createDir()
    }

    TestFile getCacheDir() {
        cacheDir
    }

    AbstractIntegrationSpec withBuildCache() {
        executer.withLocalBuildCache(cacheDir)
        this
    }

    List<TestFile> listCacheFiles() {
        cacheDir.listFiles().findAll { it.name ==~ /\p{XDigit}{32}/}.sort()
    }
}
