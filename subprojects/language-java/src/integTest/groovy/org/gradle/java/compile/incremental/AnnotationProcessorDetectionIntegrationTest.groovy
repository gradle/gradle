/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.java.compile.incremental

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.CompilationOutputsFixture
import org.gradle.language.fixtures.IncrementalAnnotationProcessorFixture
import org.gradle.language.fixtures.NonIncrementalAnnotationProcessorFixture

class AnnotationProcessorDetectionIntegrationTest extends AbstractIntegrationSpec {

    CompilationOutputsFixture outputs

    def "presence of non-incremental AP should preclude incremental build"() {
        given:
        incapProject()

        when:
        succeeds 'app:compileJava'

        then:
        executedAndNotSkipped ':inc:compileJava'
        executedAndNotSkipped ':noninc:compileJava'

        // TODO(stevey):
        //  - make sure these files are created:
        //   ./app/build/generated-sources/AppIncremental.java
        //   ./app/build/generated-sources/AppNonIncremental.java

        // Then modify ./app/src/main/java/App.java in some trivial way and:
        when:
        succeeds 'app:compileJava'

        then:
        //   - verify that it was NON-incremental, but that :app was rebuilt:
        skipped ':inc:compileJava'
        skipped ':noninc:compileJava'

        ///    - output should warn about nonIncapProcessor being non-incremental

        // Other tests:
        // when:
        //   - change app/build.gradle to remove @NonIncremental annotationProcessor
        //   - verify that the output says "all annotation processors are incremental"

        // Add a test to verify correct behavior for Issue #105.

    }

    private void incapProject() {
        file('settings.gradle') << "include 'inc'\n"
        new IncrementalAnnotationProcessorFixture().writeLibraryTo(file('inc'))
        file('settings.gradle') << "include 'noninc'\n"
        new NonIncrementalAnnotationProcessorFixture().writeLibraryTo(file('noninc'))
        appWithIncap()
    }

    private void appWithIncap() {
        subproject('app') {
            'build.gradle'("""
                apply plugin: 'java'

                configurations {
                  annotationProcessor
                }

                repositories {
                  maven {
                    url 'https://dl.bintray.com/incap/incap'
                  }
                }

                dependencies {
                  compile project(':inc')
                  compile project(':noninc')
                  annotationProcessor project(':inc')
                  annotationProcessor project(':noninc')
                }

                compileJava {
                  // Use forking to work around javac's jar cache
                  options.fork = true
                  options.annotationProcessorPath = configurations.annotationProcessor
                  options.annotationProcessorGeneratedSourcesDirectory = file("build/generated-sources")
                }
            """)
            src {
                main {
                    java {
                        'App.java'("""
                           @Incremental
                           @NonIncremental
                           class App { }
                         """)
                    }
                }
            }
        }
    }

    // TODO:  This is copied from CompileAvoidanceWithIncrementalJavaCompilationIntegrationTest.groovy.
    private void subproject(String name, @DelegatesTo(value=FileTreeBuilder, strategy = Closure.DELEGATE_FIRST) Closure<Void> config) {
        file("settings.gradle") << "include '$name'\n"
        def subprojectDir = file(name)
        subprojectDir.mkdirs()
        FileTreeBuilder builder = new FileTreeBuilder(subprojectDir)
        config.setDelegate(builder)
        config.resolveStrategy = Closure.DELEGATE_FIRST
        config.call()
    }
}
