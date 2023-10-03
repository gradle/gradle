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

package org.gradle.java.compile

import groovy.transform.SelfType
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.CompiledLanguage
import org.gradle.test.fixtures.file.TestFile

@SelfType(AbstractIntegrationSpec)
trait IncrementalCompileMultiProjectTestFixture {
    TestFile libraryAppProjectWithIncrementalCompilation(CompiledLanguage language = CompiledLanguage.JAVA) {
        multiProjectBuild('incremental', ['library', 'app'], language) {
            buildFile << """
                subprojects {
                    apply plugin: '${language.name}'
                    ${language.compileTaskName}.options.incremental = true
                }
                ${language.projectGroovyDependencies('subprojects')}

                project(':app') {
                    dependencies {
                        implementation project(':library')
                    }
                }
            """.stripIndent()
        }
        file("app/src/main/${language.name}/AClass.${language.name}") << 'public class AClass { }'
    }

    String getAppCompileTask(CompiledLanguage language = CompiledLanguage.JAVA) {
        ":app:${language.compileTaskName}"
    }

    String getLibraryCompileTask(CompiledLanguage language = CompiledLanguage.JAVA) {
        ":library:${language.compileTaskName}"
    }

    TestFile writeUnusedLibraryClass(CompiledLanguage language = CompiledLanguage.JAVA) {
        file("library/src/main/${language.name}/Unused.${language.name}") << 'public class Unused { }'
    }
}
