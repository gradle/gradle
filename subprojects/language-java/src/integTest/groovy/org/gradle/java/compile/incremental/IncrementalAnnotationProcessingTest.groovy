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

import java.nio.file.Files
import org.gradle.incap.mapping.Version1MappingFileWriter;
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.CompilationOutputsFixture
import org.gradle.language.fixtures.IncrementalAnnotationProcessorFixture
import org.gradle.language.fixtures.NonIncrementalAnnotationProcessorFixture
import org.gradle.test.fixtures.file.TestFile
import spock.lang.Ignore

class IncrementalAnnotationProcessingTest extends AbstractIntegrationSpec {

    CompilationOutputsFixture outputs

    def setup() {
        outputs = new CompilationOutputsFixture(file("app/build/classes"))
    }

    def "build should be incremental when all APs are incremental"() {
        given:
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
        given:
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
        given:
        appWithBothProcessors()
        outputs.snapshot { run "compileJava" }

        when:
        file('app/src/main/java/App.java').text = "@Incremental class App { /*removed annotation*/ }"
        succeeds 'app:compileJava'

        then:
        outputs.deletedClasses 'AppNonIncremental'
    }

    def "deleting all annotations should delete all generated files"() {
        given:
        appWithBothProcessors()
        outputs.snapshot { run "compileJava" }

        when:
        file('app/src/main/java/App.java').text = "class App { /*removed annotations*/ }"
        succeeds 'app:compileJava'

        then:
        outputs.deletedClasses 'AppIncremental', 'AppNonIncremental'
    }

    def "compile with non-incremental AP, then remove it - should do a clean build"() {
        given:
        appWithNonIncapProcessorOnly()
        outputs.snapshot { run "compileJava" }

        when:
        file('app/src/main/java/App.java').text = "class App { /*removed annotation*/ }"
        succeeds 'app:compileJava'

        then:
        outputs.deletedClasses 'AppNonIncremental'
    }

    // This test should be removed when Gradle supports aggregating processors.
    def "non-simple processor results in non-incremental build"() {
        given:
        appWithAggregatingProcessorOnly()
        outputs.snapshot { run "compileJava" }

        when:
        file('app/src/main/java/App.java').text = "@Incremental class App { /*trivial change*/ }"
        succeeds 'app:compileJava'

        then:
        outputs.recompiledClasses 'App', 'AppIncremental'
    }

    def "mapping file is created after successful compile"() {
        given:
        appWithIncapProcessorOnly()

        when:
        succeeds "compileJava"

        then:
        getMappingFile().exists()
    }

    @Ignore
    def "malformed mapping file results in full build on next compilation"() {
        given:
        appWithIncapProcessorOnly()
        run "compileJava"

        when:
        file('app/src/main/java/App.java').text = "@Incremental class App { /*trivial change*/ }"
        getMappingFile().text = "<malformed contents>"

        then:
        // TODO:  Need to get this test working.  Somehow the mapping file, whose path doesn't change,
        // is being rewritten (and repaired) between the line above and the "fails" line below.
        fails "app:compileJava"

        when:
        file('app/src/main/java/App.java').text = "@Incremental class App { /*trivial change 2*/ }"

        then:
        succeeds "app:compileJava"
    }

    def "malformed meta-inf file results in non-incremental build"() {
        given:
        appWithMalformedProcessorOnly()
        outputs.snapshot { run "compileJava" }

        when:
        file('app/src/main/java/App.java').text = "@Incremental class App { /*trivial change*/ }"
        succeeds 'app:compileJava'

        then:
        // Verify that it was non-incremental (everything recompiled).
        outputs.recompiledClasses 'App', 'B', 'AppIncremental'
    }

    def "delete generated file - Gradle recompiles the source file"() {
        given:
        appWithIncapProcessorOnly()
        run 'compileJava'
        def genFile = file('app/build/generated-sources/AppIncremental.java')

        when:
        Files.deleteIfExists(genFile.toPath())
        succeeds 'app:compileJava'

        then:
        genFile.exists()
    }

    def "unrelated java file changed - generated sources are untouched"() {
        given:
        appWithIncapProcessorOnly()
        run "compileJava"
        def genfile = file('app/build/generated-sources/AppIncremental.java')
        def timestamp = genfile.lastModified()

        when:
        file('app/src/main/java/B.java').text = "class B { /* unrelated change */ }"
        succeeds 'app:compileJava'

        then:
        outputs.recompiledClasses 'App', 'B', 'AppIncremental'
        timestamp == genfile.lastModified()
    }

    def "incremental processor correctly updates generated file when source changes"() {
        given:
        incrementalAppWithMethodsOnly()
        run "compileJava"
        def genfile = file('app/build/generated-sources/AppIncremental.java')
        def srcfile = file('app/src/main/java/App.java')

        when:
        !genfile.text.contains("public void generatedBar()")
        srcfile.text = """
            @Incremental class App {
               public void foo() {}
               public void bar() {}
            }
        """
        succeeds 'app:compileJava'

        then:
        // TODO:  Why is this recompiling B?
        outputs.recompiledClasses 'App', 'B', 'AppIncremental'
        genfile.text.contains("public void generatedBar()")
    }

    private void createIncapProcessor() {
        file('settings.gradle') << "include 'inc'\n"
        new IncrementalAnnotationProcessorFixture().writeLibraryTo(file('inc'))
    }

    private void createNonIncapProcessor() {
        file('settings.gradle') << "include 'noninc'\n"
        new NonIncrementalAnnotationProcessorFixture().writeLibraryTo(file('noninc'))
    }

    private void createBothProcessors() {
        createIncapProcessor()
        createNonIncapProcessor()
    }

    private void createAggregatingProcessor() {
        file('settings.gradle') << "include 'inc'\n"
        new AggregatingAnnotationProcessorFixture().writeLibraryTo(file('inc'))
    }

    private void createMalformedProcessor() {
        file('settings.gradle') << "include 'inc'\n"
        new MalformedAnnotationProcessorFixture().writeLibraryTo(file('inc'))
    }

    private void appWithBothProcessors() {
        createBothProcessors()
        incrementalBuildDotGradle()

        subproject('app') {
            'build.gradle'("""
                ${buildDotGradleSansDeps()}
                dependencies {
                  compileOnly project(':inc')
                  compileOnly project(':noninc')
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
        createIncapProcessor()
        writeIncrementalProcessorApp()
    }

    private void incrementalAppWithMethodsOnly() {
        createIncapProcessor()
        writeIncrementalProcessorAppWithMethods()
    }

    private void appWithNonIncapProcessorOnly() {
        createNonIncapProcessor()
        incrementalBuildDotGradle()

        subproject('app') {
            'build.gradle'("""
                ${buildDotGradleNonIncremental()}
                dependencies {
                  compileOnly project(':noninc')
                  annotationProcessor project(':noninc')
                }
            """)
            src {
                main {
                    java {
                        'App.java'("@NonIncremental class App {}")
                        'B.java'("class B {}")
                    }
                }
            }
        }
    }

    private void appWithAggregatingProcessorOnly() {
        createAggregatingProcessor()
        writeIncrementalProcessorApp()
    }

    private void appWithMalformedProcessorOnly() {
        createMalformedProcessor()
        writeIncrementalProcessorApp()
    }

    private void writeIncrementalProcessorApp() {
        incrementalBuildDotGradle()

        subproject('app') {
            'build.gradle'("""
                ${buildDotGradleSansDeps()}
                dependencies {
                  compileOnly project(':inc')
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

    private void writeIncrementalProcessorAppWithMethods() {
        writeIncrementalProcessorApp()
        file('app/src/main/java/App.java').text = """
            @Incremental class App {
               public void foo() {}
            }
        """
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
            url 'https://repo.gradle.org/gradle/libs-snapshots'
          }
        }

        compileJava {
          options.incrementalAnnotationProcessing = true
          options.annotationProcessorPath = configurations.annotationProcessor
          options.annotationProcessorGeneratedSourcesDirectory = file("build/generated-sources")
        }
        """
    }

    private String buildDotGradleNonIncremental() {
        """
        apply plugin: 'java'

        configurations {
          annotationProcessor
        }

        compileJava {
          options.annotationProcessorPath = configurations.annotationProcessor
          options.annotationProcessorGeneratedSourcesDirectory = file("build/generated-sources")
        }
        """
    }

    private void incrementalBuildDotGradle() {
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

    private File getMappingFile() {
        file('app/build/tmp/annotationProcessing/VERSION_1_mapping.txt')
    }

    class AggregatingAnnotationProcessorFixture extends IncrementalAnnotationProcessorFixture {
        @Override
        protected void writeIncapSupportLevel(TestFile projectDir) {
            projectDir.file(INCAP_TAG_FILE_NAME).text = "aggregating"
        }
    }

    class MalformedAnnotationProcessorFixture extends IncrementalAnnotationProcessorFixture {
        @Override
        protected void writeIncapSupportLevel(TestFile projectDir) {
            projectDir.file(INCAP_TAG_FILE_NAME).text = "malformed"
        }
    }
}
