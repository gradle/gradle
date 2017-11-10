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

    def setup() {
        outputs = new CompilationOutputsFixture(file("app/build/classes"))
        createBothProcessors()
    }

    def "build should be incremental when all APs are incremental"() {
        appWithIncapProcessorOnly()
        outputs.snapshot { run "compileJava" }

        when:
        succeeds 'app:compileJava'

        then:
        file('app/build/generated-sources/AppIncremental.java').exists()
        !file('app/build/generated-sources/AppNonIncremental.java').exists()

        when:
        // Modify main source file.  Would normally be incremental.
        file('app/src/main/java/App.java').text = "@Incremental class App { /*change*/ }"
        succeeds 'app:compileJava'

        then:
        skipped ':inc:compileJava'

        // Verify that it was incremental (B not recompiled).
        outputs.recompiledClasses 'App', 'AppIncremental'
    }

    def "presence of non-incremental AP should force non-incremental build"() {
        appWithBothProcessors()
        outputs.snapshot { run "compileJava" }

        when:
        succeeds 'app:compileJava'

        then:
        file('app/build/generated-sources/AppIncremental.java').exists()
        file('app/build/generated-sources/AppNonIncremental.java').exists()

        when:
        // Trivial reformatting of the main source file.  Would normally be incremental.
        file('app/src/main/java/App.java').text = "@Incremental @NonIncremental class App { /*change*/ }"
        succeeds 'app:compileJava'

        then:
        skipped ':inc:compileJava'
        skipped ':noninc:compileJava'

        // Verify app was rebuilt non-incrementally.
        outputs.recompiledClasses 'App', 'B', 'AppNonIncremental', 'AppIncremental'
    }

    def "deleting an annotation should delete its generated file"() {
        appWithBothProcessors()
        outputs.snapshot { run "compileJava" }

        when:
        file('app/src/main/java/App.java').text = "@Incremental class App { /*removed annotation*/ }"
        succeeds 'app:compileJava'

        then:
        outputs.deletedClasses 'AppNonIncremental'
    }

    private void createBothProcessors() {
        file('settings.gradle') << "include 'inc'\n"
        new IncrementalAnnotationProcessorFixture().writeLibraryTo(file('inc'))
        file('settings.gradle') << "include 'noninc'\n"
        new NonIncrementalAnnotationProcessorFixture().writeLibraryTo(file('noninc'))
    }

    private void appWithBothProcessors() {
        incrementalBuild()

        subproject('app') {
            'build.gradle'("""
                ${buildDotGradleSansDeps()}
                dependencies {
                  compile project(':inc')
                  compile project(':noninc')
                  annotationProcessor project(':inc')
                  annotationProcessor project(':noninc')
                }
            """)
            src {
                main {
                    java {
                        'App.java'("@Incremental @NonIncremental class App {}")
                        'B.java'("class B {}")
                    }
                }
            }
        }
    }

    private void appWithIncapProcessorOnly() {
        incrementalBuild()

        subproject('app') {
            'build.gradle'("""
                ${buildDotGradleSansDeps()}
                dependencies {
                  compile project(':inc')
                  annotationProcessor project(':inc')
                }
            """)
            src {
                main {
                    java {
                        'App.java'("@Incremental class App {}")
                        'B.java'("class B {}")
                    }
                }
            }
        }
    }

    private String buildDotGradleSansDeps() {
        """
        apply plugin: 'java'

        configurations {
          annotationProcessor
        }

        repositories {
          maven {
            url 'https://dl.bintray.com/incap/incap'
          }
        }

        compileJava {
          options.incrementalAnnotationProcessing = true
          options.annotationProcessorPath = configurations.annotationProcessor
          options.annotationProcessorGeneratedSourcesDirectory = file("build/generated-sources")
        }
        """
    }

    private void incrementalBuild() {
        file('build.gradle') << """
            allprojects {
                tasks.withType(JavaCompile) {
                    options.incremental = true
                }
            }
        """
    }

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
