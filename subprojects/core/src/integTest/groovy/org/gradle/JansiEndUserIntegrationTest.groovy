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

package org.gradle

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.util.Requires
import spock.lang.Ignore
import spock.lang.Issue

import static org.gradle.internal.nativeintegration.jansi.JansiBootPathConfigurer.JANSI_LIBRARY_PATH_SYS_PROP
import static org.gradle.util.TestPrecondition.JDK8_OR_EARLIER

class JansiEndUserIntegrationTest extends AbstractIntegrationSpec {

    private final static String JANSI_VERSION = '1.11'

    @Issue("GRADLE-3573")
    def "test workers use a different version of Jansi than initialized by Gradle's native services"() {
        given:
        buildFile << basicJavaProject()
        buildFile << """
            dependencies {
                testCompile 'org.fusesource.jansi:jansi:$JANSI_VERSION'
                testCompile 'junit:junit:4.12'
            }
        """

        file('src/test/java/org/gradle/JansiTest.java') << """
            package org.gradle;

            import org.fusesource.jansi.Ansi;

            import org.junit.Test;
            import static org.junit.Assert.assertNull;
            import static org.junit.Assert.assertEquals;

            public class JansiTest {
                @Test
                public void canUseCustomJansiVersion() {
                    assertNull(System.getProperty("${JANSI_LIBRARY_PATH_SYS_PROP}"));
                    assertEquals(Ansi.class.getPackage().getImplementationVersion(), "$JANSI_VERSION");
                }
            }
        """

        when:
        succeeds('test')

        then:
        executedAndNotSkipped(':test')
    }

    @Ignore
    @Issue("GRADLE-3578")
    def "java compiler uses a different version of Jansi than initialized by Gradle's native services"() {
        when:
        AnnotationProcessorPublisher annotationProcessorPublisher = new AnnotationProcessorPublisher()
        annotationProcessorPublisher.writeSourceFiles()
        inDirectory(annotationProcessorPublisher.projectDir).withTasks('publish').run()

        then:
        annotationProcessorPublisher.publishedJarFile.isFile()
        annotationProcessorPublisher.publishedPomFile.isFile()

        when:
        buildFile << basicJavaProject()
        buildFile << annotationProcessorDependency(annotationProcessorPublisher.repoDir, annotationProcessorPublisher.dependencyCoordinates)
        buildFile << """
            compileJava {
                options.annotationProcessorPath = configurations.customAnnotation
            }
        """

        file('src/main/java/MyClass.java') << """
            public class MyClass {}
        """

        succeeds 'compileJava'

        then:
        outputContains('Hello World')
    }

    def "groovy compiler uses a different version of Jansi than initialized by Gradle's native services"() {
        when:
        AnnotationProcessorPublisher annotationProcessorPublisher = new AnnotationProcessorPublisher()
        annotationProcessorPublisher.writeSourceFiles()
        inDirectory(annotationProcessorPublisher.projectDir).withTasks('publish').run()

        then:
        annotationProcessorPublisher.publishedJarFile.isFile()
        annotationProcessorPublisher.publishedPomFile.isFile()

        when:
        buildFile << basicJavaProject()
        buildFile << annotationProcessorDependency(annotationProcessorPublisher.repoDir, annotationProcessorPublisher.dependencyCoordinates)
        buildFile << """
            apply plugin: 'groovy'

            dependencies {
                compile localGroovy()
            }

            compileGroovy {
                groovyOptions.javaAnnotationProcessing = true
                options.annotationProcessorPath = configurations.customAnnotation
            }
        """

        file('src/main/groovy/MyClass.groovy') << """
            class MyClass {}
        """

        succeeds 'compileGroovy'

        then:
        outputContains('Hello World')
    }

    @Requires(JDK8_OR_EARLIER)
    def "kotlin compiler bundles different version of Jansi than initialized by Gradle's native services"() {
        given:
        def kotlinVersion = '1.0.4'

        buildFile << """
            buildscript {
                ${mavenCentralRepository()}
                dependencies {
                    classpath 'org.jetbrains.kotlin:kotlin-gradle-plugin:$kotlinVersion'
                }
            }

            apply plugin: 'kotlin'

            ${mavenCentralRepository()}

            dependencies {
                compile 'org.jetbrains.kotlin:kotlin-stdlib:$kotlinVersion'
            }
        """

        when:
        file('src/main/kotlin/MyClass.kt') << """
            class MyClass {}
        """

        executer.expectDeprecationWarning()
        succeeds 'compileKotlin'

        then:
        executedAndNotSkipped(':compileKotlin')

        when:
        file('src/main/kotlin/FailingClass.kt') << """
            class FailingClass { < }
        """

        executer.expectDeprecationWarning()
        fails 'compileKotlin'

        then:
        executedAndNotSkipped(':compileKotlin')
        failure.assertHasCause('Compilation error. See log for more details')
        failure.assertHasErrorOutput('FailingClass.kt: (2, 34): Expecting member declaration')
    }

    static String basicJavaProject() {
        """
            apply plugin: 'java'

            ${mavenCentralRepository()}
        """
    }

    static String annotationProcessorDependency(File repoDir, String processorDependency) {
        """
            sourceCompatibility = '1.7'

            repositories {
                maven {
                    url '${repoDir.toURI()}'
                }
            }

            configurations {
                customAnnotation
            }

            dependencies {
                customAnnotation '$processorDependency'
            }
        """
    }

    private class AnnotationProcessorPublisher {
        private final String group = 'org.gradle'
        private final String name = 'processor'
        private final String version = '1.0'

        String getProjectDir() {
            name
        }

        String getDependencyCoordinates() {
            "$group:$name:$version"
        }

        File getRepoDir() {
            file("$name/build/repo")
        }

        File getPublishedJarFile() {
            new File(getArtifactPublishDir(), "${name}-${version}.jar")
        }

        File getPublishedPomFile() {
            new File(getArtifactPublishDir(), "${name}-${version}.pom")
        }

        private File getArtifactPublishDir() {
            file("$name/build/repo/${group.replaceAll('\\.', '/')}/$name/$version")
        }

        void writeSourceFiles() {
            writeBuildFile()
            writeProcessorSourceFile()
            writeMetaInfService()
        }

        private void writeBuildFile() {
            def processorBuildFile = file("$name/build.gradle")
            processorBuildFile << basicJavaProject()
            processorBuildFile << """
                apply plugin: 'maven-publish'

                group = '$group'
                version = '$version'
                sourceCompatibility = '1.7'

                dependencies {
                    compile 'org.fusesource.jansi:jansi:$JANSI_VERSION'
                }

                publishing {
                   publications {
                        mavenJava(MavenPublication) {
                            from components.java
                        }
                    }

                    repositories {
                        maven {
                            url "\$buildDir/repo"
                        }
                    }
                }
            """
        }

        private void writeProcessorSourceFile() {
            file("$name/src/main/java/org/gradle/MyProcessor.java") << """
                package org.gradle;

                import javax.annotation.processing.*;
                import javax.lang.model.SourceVersion;
                import javax.lang.model.element.TypeElement;
                import java.util.Set;
                import java.io.PrintStream;

                import org.fusesource.jansi.AnsiConsole;

                @SupportedAnnotationTypes({"org.gradle.Custom"})
                @SupportedSourceVersion(SourceVersion.RELEASE_7)
                public class MyProcessor extends AbstractProcessor {
                    @Override
                    public synchronized void init(ProcessingEnvironment processingEnv) {
                        super.init(processingEnv);
                        PrintStream console = AnsiConsole.out();
                        console.println("Hello World");
                        console.flush();
                    }

                    @Override
                    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
                        return false;
                    }
                }
            """
        }

        private void writeMetaInfService() {
            file("$name/src/main/resources/META-INF/services/javax.annotation.processing.Processor") << 'org.gradle.MyProcessor'
        }
    }
}
