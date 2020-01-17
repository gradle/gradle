/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.integtests.fixtures.kotlin.dsl

import groovy.io.FileType
import org.gradle.integtests.fixtures.RepoScriptBlockUtil
import org.gradle.integtests.fixtures.executer.GradleExecuter
import org.gradle.test.fixtures.file.TestFile

class KotlinEapRepoUtil {

    static void withKotlinEapRepository(TestFile baseDir, GradleExecuter executer) {
        def eapRepoInit = baseDir.file("kotlin-eap-repo.init.gradle") << """
            allprojects {
                repositories {
                    ${RepoScriptBlockUtil.kotlinEapRepositoryDefinition()}
                }
            }
        """
        executer.beforeExecute {
            it.withArguments("-I", eapRepoInit.canonicalPath)
        }
    }

    static void withKotlinEapRepoInAllKotlinBuildSrc(File rootDir) {
        rootDir.traverse(type: FileType.FILES, nameFilter: ~/build\.gradle\.kts/) { script ->
            if(script.parentFile.name == 'buildSrc') {
                withKotlinEapRepoInKotlinBuildSrc(script.parentFile.parentFile)
            }
        }
    }

    static void withKotlinEapRepoInKotlinBuildSrc(File rootDir) {
        def buildSrcBuildScript = new File(rootDir, "buildSrc/build.gradle.kts")
        assert buildSrcBuildScript.isFile()
        buildSrcBuildScript.text = buildSrcBuildScript.text + """
            repositories {
                ${RepoScriptBlockUtil.kotlinEapRepositoryDefinition()}
            }
        """
    }

    static File createKotlinEapInitScript() {
        File initScript = File.createTempFile("kotlin-eap-repo", ".gradle")
        initScript.deleteOnExit()
        initScript << kotlinEapRepoInitScript()
        return initScript
    }

    private static String kotlinEapRepoInitScript() {
        return """
            settingsEvaluated {
                it.pluginManagement {
                    it.repositories {
                        gradlePluginPortal()
                        ${RepoScriptBlockUtil.kotlinEapRepositoryDefinition()}
                    }
                }
            }
            allprojects {
                repositories {
                    ${RepoScriptBlockUtil.kotlinEapRepositoryDefinition()}
                }
            }
        """
    }
}
