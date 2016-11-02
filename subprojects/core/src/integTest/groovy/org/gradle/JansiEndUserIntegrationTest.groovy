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

import groovy.transform.NotYetImplemented
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import spock.lang.Issue

import static org.gradle.internal.nativeintegration.jansi.JansiBootPathConfigurer.JANSI_LIBRARY_PATH_SYS_PROP

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

    @NotYetImplemented
    @Issue("GRADLE-3578")
    def "java compiler uses a different version of Jansi than initialized by Gradle's native services"() {
        when:
        def processorGroup = 'org.gradle'
        def processorName = 'processor'
        def processorVersion = '1.0'

        def processorBuildFile = file("$processorName/build.gradle")
        processorBuildFile << basicJavaProject()
        processorBuildFile << """
            apply plugin: 'maven-publish'

            group = '$processorGroup'
            version = '$processorVersion'
            sourceCompatibility = '1.6'

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

        file("$processorName/src/main/java/org/gradle/MyProcessor.java") << """
            package org.gradle;

            import javax.annotation.processing.*;
            import javax.lang.model.SourceVersion;
            import javax.lang.model.element.TypeElement;
            import java.util.Set;
            import java.io.PrintStream;

            import org.fusesource.jansi.AnsiConsole;

            @SupportedAnnotationTypes({"org.gradle.Custom"})
            @SupportedSourceVersion(SourceVersion.RELEASE_6)
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

        file("$processorName/src/main/resources/META-INF/services/javax.annotation.processing.Processor") << 'org.gradle.MyProcessor'

        inDirectory(processorName).withTasks('publish').run()

        then:
        String repoArtifactDir = "$processorName/build/repo/${processorGroup.replaceAll('\\.', '/')}/$processorName/$processorVersion"
        file("$repoArtifactDir/${processorName}-${processorVersion}.jar").isFile()
        file("$repoArtifactDir/${processorName}-${processorVersion}.pom").isFile()

        when:
        buildFile << basicJavaProject()
        buildFile << """
            sourceCompatibility = '1.6'

            repositories {
                maven {
                    url '${file("$processorName/build/repo")}'
                }
            }

            configurations {
                customAnnotation
            }

            dependencies {
                customAnnotation '$processorGroup:$processorName:$processorVersion'
            }

            compileJava {
                options.compilerArgs += ['-processorpath', configurations.customAnnotation.asPath]
            }
        """

        file('src/main/java/MyClass.java') << """
            public class MyClass {}
        """

        requireGradleDistribution()
        succeeds 'compileJava'

        then:
        outputContains('Hello World')
    }

    static String basicJavaProject() {
        """
            apply plugin: 'java'

            repositories {
                mavenCentral()
            }
        """
    }
}
