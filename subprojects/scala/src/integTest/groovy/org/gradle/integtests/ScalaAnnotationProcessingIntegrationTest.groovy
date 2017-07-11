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

class ScalaAnnotationProcessingIntegrationTest extends AbstractIntegrationSpec {

    def "cannot compile annotated Java class if annotation processor library is not available on classpath"() {
        when:
        buildFile << basicScalaProject()
        file('src/main/java/MyClass.java') << annotatedJavaClass()

        fails 'compileScala'

        then:
        errorOutput.contains('Compilation failed; see the compiler error output for details.')
        errorOutput.contains('package org.gradle does not exist')
    }

    def "processes annotation for Java class if annotation processor is available on classpath"() {
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
        file('src/main/java/MyClass.java') << annotatedJavaClass()

        succeeds 'compileScala'

        then:
        outputContains('Hello World')
    }

    static String basicJavaProject() {
        """
            apply plugin: 'java'
        """
    }

    static String basicScalaProject() {
        """
            apply plugin: 'scala'

            repositories {
                mavenCentral()
            }
            
            dependencies {
                compile 'org.scala-lang:scala-library:2.12.2'
            }
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

            dependencies {
                compileOnly '$processorDependency'
            }
        """
    }

    static String annotatedJavaClass() {
        """
            import org.gradle.Custom;
            
            @Custom
            public class MyClass {}
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
                import java.util.Set;
                import java.io.PrintStream;

                @SupportedAnnotationTypes({"org.gradle.Custom"})
                @SupportedSourceVersion(SourceVersion.RELEASE_7)
                public class MyProcessor extends AbstractProcessor {
                    @Override
                    public synchronized void init(ProcessingEnvironment processingEnv) {
                        super.init(processingEnv);
                        System.out.println("Hello World");
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
