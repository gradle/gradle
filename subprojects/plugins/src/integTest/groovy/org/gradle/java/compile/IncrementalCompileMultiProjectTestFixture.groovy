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

package org.gradle.java.compile

import groovy.transform.SelfType
import org.gradle.integtests.fixtures.AbstractIntegrationSpec

@SelfType(AbstractIntegrationSpec)
trait IncrementalCompileMultiProjectTestFixture {
    def libraryAppProjectWithIncrementalCompilation() {
        multiProjectBuild('incremental', ['library', 'app']) {
            buildFile << '''
                subprojects {
                    apply plugin: 'java'
                    
                    tasks.withType(JavaCompile) {
                        it.options.incremental = true
                    }
                }
                
                project(':app') {
                    dependencies {
                        compile project(':library')
                    }
                }
            '''.stripIndent()
        }
        file('app/src/main/java/AClass.java') << 'public class AClass { }'
    }

    def getAppCompileJava() {
        ':app:compileJava'
    }

    def getLibraryCompileJava() {
        ':library:compileJava'
    }

    def writeUnusedLibraryClass() {
        file('library/src/main/java/Unused.java') << 'public class Unused { }'
    }
}
