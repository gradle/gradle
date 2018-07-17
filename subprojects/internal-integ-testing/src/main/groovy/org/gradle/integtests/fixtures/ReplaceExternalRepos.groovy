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

package org.gradle.integtests.fixtures

import org.gradle.test.fixtures.dsl.GradleDsl

import static org.gradle.integtests.fixtures.RepoScriptBlockUtil.MirroredRepository.GOOGLE
import static org.gradle.integtests.fixtures.RepoScriptBlockUtil.MirroredRepository.JCENTER
import static org.gradle.integtests.fixtures.RepoScriptBlockUtil.MirroredRepository.MAVEN_CENTRAL

class ReplaceExternalRepos {
    static void replaceExternalRepos(File rootDir) {
        if (rootDir != null && rootDir.isDirectory()) {
            rootDir.eachFileRecurse { file ->
                if (file.name == 'build.gradle') {
                    replaceRepositoriesInBuildFile(file, GradleDsl.GROOVY)
                } else if (file.name == 'build.gradle.kts') {
                    replaceRepositoriesInBuildFile(file, GradleDsl.KOTLIN)
                }
            }
        }
    }

    static replaceRepositoriesInBuildFile(File file, GradleDsl dsl) {
        String text = file.text
        [JCENTER, MAVEN_CENTRAL, GOOGLE].each {
            text = text.replace(it.declaration, it.getRepositoryDefinition(dsl))
        }
        file.text = text
    }
}
