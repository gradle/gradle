/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.integtests

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.UnitTestPreconditions
import spock.lang.Issue

import static org.gradle.util.internal.TextUtil.escapeString

@Issue("https://github.com/gradle/gradle/issues/1498")
class ScalaAnnotationProcessingIntegrationTest extends AbstractIntegrationSpec {

    def "cannot compile annotated Java class if annotation processor library is not available on classpath"() {
        given:
        buildFile << basicScalaProject()
        file('src/main/scala/MyClass.java') << javaClassWithCustomAnnotation()

        when:
        fails 'compileScala'

        then:
        skipped(':compileJava')
        executedAndNotSkipped(':compileScala')
        result.assertHasErrorOutput('error: package org.gradle does not exist')
        failure.assertHasCause('javac returned non-zero exit code')
    }

    def "does not process annotation for Java class if annotation processor is only available on classpath"() {
        when:
        AnnotationProcessorPublisher annotationProcessorPublisher = new AnnotationProcessorPublisher()
        annotationProcessorPublisher.writeSourceFiles()
        inDirectory(annotationProcessorPublisher.projectDir).withTasks('publish').run()

        then:
        annotationProcessorPublisher.publishedJarFile.isFile()
        annotationProcessorPublisher.publishedPomFile.isFile()

        when:
        buildFile << basicScalaProject()
        buildFile << annotationProcessorDependency(annotationProcessorPublisher.repoDir, annotationProcessorPublisher.dependencyCoordinates)
        file('src/main/scala/MyClass.java') << javaClassWithCustomAnnotation()

        succeeds 'compileScala'

        then:
        skipped(':compileJava')
        executedAndNotSkipped(':compileScala')
        new TestFile(testDirectory, 'generated.txt').assertDoesNotExist()
    }

    def "processes annotation for Java class if annotation processor is available on processor path"() {
        when:
        AnnotationProcessorPublisher annotationProcessorPublisher = new AnnotationProcessorPublisher()
        annotationProcessorPublisher.writeSourceFiles()
        inDirectory(annotationProcessorPublisher.projectDir).withTasks('publish').run()

        then:
        annotationProcessorPublisher.publishedJarFile.isFile()
        annotationProcessorPublisher.publishedPomFile.isFile()

        when:
        buildFile << basicScalaProject()
        buildFile << annotationProcessorDependency(annotationProcessorPublisher.repoDir, annotationProcessorPublisher.dependencyCoordinates)
        buildFile << """
            configurations.annotationProcessor.extendsFrom configurations.compileOnly
        """
        file('src/main/scala/MyClass.java') << javaClassWithCustomAnnotation()

        succeeds 'compileScala'

        then:
        skipped(':compileJava')
        executedAndNotSkipped(':compileScala')
        new File(testDirectory, 'generated.txt').exists()
    }

    def "processes annotation for Java class with processor option if annotation processor is available on processor path"() {
        when:
        AnnotationProcessorPublisher annotationProcessorPublisher = new AnnotationProcessorPublisher()
        annotationProcessorPublisher.writeSourceFiles()
        inDirectory(annotationProcessorPublisher.projectDir).withTasks('publish').run()

        then:
        annotationProcessorPublisher.publishedJarFile.isFile()
        annotationProcessorPublisher.publishedPomFile.isFile()

        when:
        buildFile << basicScalaProject()
        buildFile << annotationProcessorDependency(annotationProcessorPublisher.repoDir, annotationProcessorPublisher.dependencyCoordinates)
        buildFile << """
            configurations.annotationProcessor.extendsFrom configurations.compileOnly
            compileScala.options.compilerArgumentProviders.add(new FileNameProvider("foo"))

            class FileNameProvider implements CommandLineArgumentProvider {
                @Internal
                String fileName

                FileNameProvider(String fileName) {
                    this.fileName = fileName
                }

                @Override
                List<String> asArguments() {
                    ["-AfileName=\${fileName}".toString()]
                }
            }
        """
        file('src/main/scala/MyClass.java') << javaClassWithCustomAnnotation()

        succeeds 'compileScala'

        then:
        skipped(':compileJava')
        executedAndNotSkipped(':compileScala')
        new File(testDirectory, 'foo.txt').exists()
    }

    def "cannot use external annotation processor for Java class, from classpath"() {
        given:
        buildFile << basicScalaProject()
        buildFile << lombokDependency()
        file('src/main/scala/Test.java') << javaClassWithLombokAnnotation()

        when:
        fails 'compileScala'

        then:
        skipped(':compileJava')
        executedAndNotSkipped(':compileScala')
    }

    // https://github.com/rzwitserloot/lombok/issues/2681
    @Requires(UnitTestPreconditions.Jdk15OrEarlier)
    def "can use external annotation processor for Java class, from processor path"() {
        given:
        buildFile << basicScalaProject()
        buildFile << lombokDependency()
        buildFile << """
            configurations.annotationProcessor.extendsFrom configurations.compileOnly
        """
        file('src/main/scala/Test.java') << javaClassWithLombokAnnotation()

        when:
        succeeds 'compileScala'

        then:
        skipped(':compileJava')
        executedAndNotSkipped(':compileScala')
    }

    static String basicScalaProject() {
        """
            plugins {
                id("scala")
            }

            ${mavenCentralRepository()}

            dependencies {
                implementation 'org.scala-lang:scala-library:2.11.12'
            }
        """
    }

    static String annotationProcessorDependency(File repoDir, String processorDependency) {
        """
            java.sourceCompatibility = '1.7'

            repositories {
                maven {
                    url '${repoDir.toURI()}'
                }
            }

            dependencies {
                compileOnly '$processorDependency'
            }
        """
    }

    static String lombokDependency() {
        """
            dependencies {
                compileOnly 'org.projectlombok:lombok:1.18.18'
            }
        """
    }

    static String javaClassWithCustomAnnotation() {
        """
            @org.gradle.Custom
            public class MyClass {}
        """
    }

    static String javaClassWithLombokAnnotation() {
        """
            @lombok.Value
            public class Test {
                String test;

                static {
                    new Test("test").getTest();
                }
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
            processorBuildFile << """
                apply plugin: 'java'
                apply plugin: 'maven-publish'

                group = '$group'
                version = '$version'
                java.sourceCompatibility = '1.7'

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
            file("$name/src/main/java/org/gradle/Custom.java") << """
                package org.gradle;

                import java.lang.annotation.*;

                @Target(ElementType.TYPE)
                @Retention(RetentionPolicy.CLASS)
                public @interface Custom {}
            """

            file("$name/src/main/java/org/gradle/MyProcessor.java") << """
                package org.gradle;

                import javax.annotation.processing.*;
                import javax.lang.model.SourceVersion;
                import javax.lang.model.element.TypeElement;
                import java.util.Collections;
                import java.util.Set;
                import java.io.PrintStream;
                import java.io.File;
                import java.io.IOException;

                @SupportedAnnotationTypes({"org.gradle.Custom"})
                @SupportedSourceVersion(SourceVersion.RELEASE_7)
                public class MyProcessor extends AbstractProcessor {
                    @Override
                    public synchronized void init(ProcessingEnvironment processingEnv) {
                        super.init(processingEnv);

                        String fileName = processingEnv.getOptions().getOrDefault("fileName", "generated");

                        try {
                            new File("${escapeString(testDirectory.absolutePath)}/" + fileName + ".txt").createNewFile();
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    }

                    @Override
                    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
                        return false;
                    }

                    @Override
                    public Set<String> getSupportedOptions() {
                        return Collections.singleton("fileName");
                    }
                }
            """
        }

        private void writeMetaInfService() {
            file("$name/src/main/resources/META-INF/services/javax.annotation.processing.Processor") << 'org.gradle.MyProcessor'
        }
    }
}
