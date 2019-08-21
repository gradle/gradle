/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.instantexecution

import org.gradle.integtests.fixtures.TestResources
import org.gradle.integtests.fixtures.android.AndroidHome
import org.junit.Rule


class InstantExecutionAndroidIntegrationTest extends AbstractInstantExecutionIntegrationTest {

    @Rule
    TestResources resources = new TestResources(temporaryFolder, "builds")

    def instantExecution

    def setup() {
        AndroidHome.assumeIsSet()
        executer.noDeprecationChecks()
        executer.beforeExecute {
            withRepositoryMirrors()
            inDirectory(file("android-3.6-mini"))
        }
        instantExecution = newInstantExecutionFixture()
    }

    def "android 3.6 minimal build assembleDebug --dry-run"() {

        when:
        instantRun("assembleDebug", "--dry-run")

        then:
        instantExecution.assertStateStored()

        when:
        instantRun("assembleDebug", "--dry-run")

        then:
        instantExecution.assertStateLoaded()
    }

    def tasksToAssembleDebug = [
        ":app:preBuild",
        ":app:preDebugBuild",
        ":app:compileDebugAidl",
        ":app:compileDebugRenderscript",
        ":app:checkDebugManifest",
        ":app:generateDebugBuildConfig",
        ":app:javaPreCompileDebug",
        ":app:mainApkListPersistenceDebug",
        ":app:generateDebugResValues",
        ":app:generateDebugResources",
        ":app:mergeDebugResources",
        ":app:createDebugCompatibleScreenManifests",
        ":app:extractDeepLinksDebug",
        ":app:processDebugManifest",
        ":app:processDebugResources",
        ":app:compileDebugJavaWithJavac",
        ":app:compileDebugSources",
        ":app:mergeDebugShaders",
        ":app:compileDebugShaders",
        ":app:generateDebugAssets",
        ":app:mergeDebugAssets",
        ":app:processDebugJavaRes",
        ":app:mergeDebugJavaResource",
        ":app:checkDebugDuplicateClasses",
        ":app:mergeExtDexDebug",
        ":app:mergeLibDexDebug",
        ":app:dexBuilderDebug",
        ":app:mergeProjectDexDebug",
        ":app:validateSigningDebug",
        ":app:signingConfigWriterDebug",
        ":app:mergeDebugJniLibFolders",
        ":app:mergeDebugNativeLibs",
        ":app:stripDebugDebugSymbols",
        ":app:packageDebug",
        ":app:assembleDebug"
    ]

    def "android 3.6 minimal build supported tasks up-to-date"() {

        given:
        def tasks = tasksToAssembleDebug - [
            // unsupported tasks
            ":app:processDebugResources",
            ":app:compileDebugJavaWithJavac",
            ":app:compileDebugSources",
            ":app:dexBuilderDebug",
            ":app:mergeProjectDexDebug",
            ":app:packageDebug",
            ":app:assembleDebug",
        ]

        when:
        instantRun(*tasks)

        then:
        instantExecution.assertStateStored()

        when:
        instantRun(*tasks)

        then:
        instantExecution.assertStateLoaded()
    }

    def "android 3.6 minimal build supported tasks clean build"() {

        given:
        def tasks = tasksToAssembleDebug - [
            // unsupported tasks
            ":app:processDebugResources",
            ":app:compileDebugJavaWithJavac",
            ":app:compileDebugSources",
            ":app:dexBuilderDebug",
            ":app:mergeProjectDexDebug",
            ":app:packageDebug",
            ":app:assembleDebug"
        ]

        when:
        instantRun(*tasks)

        then:
        instantExecution.assertStateStored()

        when:
        run 'clean'
        instantRun(*tasks)

        then:
        instantExecution.assertStateLoaded()
    }
}
