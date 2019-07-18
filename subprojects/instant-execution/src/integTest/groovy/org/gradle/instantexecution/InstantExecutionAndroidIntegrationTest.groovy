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

    def setup() {
        AndroidHome.assumeIsSet()
        executer.noDeprecationChecks()
    }

    def "android 3.5 minimal build assembleDebug"() {

        executer.beforeExecute {
            inDirectory(file("android-3.5-mini"))
        }
        def instantExecution = newInstantExecutionFixture()

        given:
        def tasks = [
            ":app:preBuild",
            ":app:preDebugBuild",
            // ":app:compileDebugAidl",
            ":app:compileDebugRenderscript",
            ":app:checkDebugManifest",
            ":app:generateDebugBuildConfig",
            ":app:mainApkListPersistenceDebug",
            ":app:generateDebugResValues",
            ":app:generateDebugResources",
            // ":app:createDebugCompatibleScreenManifests",
            // ":app:processDebugManifest",
            // ":app:mergeDebugShaders",
            // ":app:compileDebugShaders",
            // ":app:generateDebugAssets",
            // ":app:mergeDebugAssets",
            ":app:processDebugJavaRes",
            // ":app:checkDebugDuplicateClasses",
            ":app:validateSigningDebug",
            // ":app:signingConfigWriterDebug",
            // ":app:mergeDebugJniLibFolders",
            // ":app:javaPreCompileDebug",
            // ":app:mergeDebugResources",
            // ":app:processDebugResources",
            // ":app:compileDebugJavaWithJavac",
            // ":app:compileDebugSources",
            // ":app:mergeDebugNativeLibs",
            // ":app:transformClassesWithDexBuilderForDebug",
            // ":app:mergeLibDexDebug",
            // ":app:stripDebugDebugSymbols",
            // ":app:mergeDebugJavaResource",
            // ":app:mergeExtDexDebug",
            // ":app:mergeProjectDexDebug",
            // ":app:packageDebug",
            // ":app:assembleDebug",
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
}
