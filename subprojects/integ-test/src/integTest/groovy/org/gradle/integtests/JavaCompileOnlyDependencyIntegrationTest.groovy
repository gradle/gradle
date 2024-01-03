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

package org.gradle.integtests

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

import static org.gradle.util.internal.TextUtil.normaliseFileSeparators

class JavaCompileOnlyDependencyIntegrationTest extends AbstractIntegrationSpec {
    def "can compile against compile only dependency"() {
        given:
        file('src/main/java/Test.java') << """
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class Test {
    private static final Log logger = LogFactory.getLog(Test.class);
}
"""

        and:
        buildFile << """
apply plugin: 'java'

${mavenCentralRepository()}

dependencies {
    compileOnly 'commons-logging:commons-logging:1.2'
}
"""

        when:
        run('compileJava')

        then:
        javaClassFile('Test.class').exists()
    }

    def "production compile only dependencies not visible to tests"() {
        given:
        file('src/test/java/Test.java') << """
import org.apache.commons.logging.Log;

public class Test {
    private Log logger;
}
"""

        and:
        buildFile << """
apply plugin: 'java'

${mavenCentralRepository()}

dependencies {
    compileOnly 'commons-logging:commons-logging:1.2'
}
"""

        when:
        def failure = fails('compileTestJava')

        then:
        failure.assertHasCause("Compilation failed; see the compiler error output for details.")
        failure.assertHasErrorOutput("package org.apache.commons.logging does not exist")
    }

    def "compile only dependencies not included in runtime classpath"() {
        given:
        def implementationModule = mavenRepo.module('org.gradle.test', 'implementation', '1.2').publish()
        def compileOnlyModule = mavenRepo.module('org.gradle.test', 'compileOnly', '1.0').publish()
        def runtimeModule = mavenRepo.module('org.gradle.test', 'runtime', '1.1').publish()

        buildFile << """
apply plugin: 'java'

repositories {
    maven { url '$mavenRepo.uri' }
}

dependencies {
    implementation 'org.gradle.test:implementation:1.2'
    compileOnly 'org.gradle.test:compileOnly:1.0'
    runtimeOnly 'org.gradle.test:runtime:1.1'
}

task checkCompileClasspath {
    def compileClasspath = configurations.compileClasspath
    def expectedFiles = provider {
        [file('${normaliseFileSeparators(compileOnlyModule.artifactFile.path)}'), file('${normaliseFileSeparators(implementationModule.artifactFile.path)}')] as Set
    }
    doLast {
        assert compileClasspath.files == expectedFiles.get()
    }
}

task checkRuntimeClasspath {
    def runtimeClasspath = configurations.runtimeClasspath
    def expectedFiles = provider {
        [file('${normaliseFileSeparators(implementationModule.artifactFile.path)}'), file('${normaliseFileSeparators(runtimeModule.artifactFile.path)}')] as Set
    }
    doLast {
        assert runtimeClasspath.files == expectedFiles.get()
    }
}
"""

        expect:
        succeeds('checkCompileClasspath', 'checkRuntimeClasspath')
    }

    def "conflict resolution between implementation and compile only dependencies"() {
        given:
        def shared10 = mavenRepo.module('org.gradle.test', 'shared', '1.0').publish()
        def shared11 = mavenRepo.module('org.gradle.test', 'shared', '1.1').publish()
        def implementationModule = mavenRepo.module('org.gradle.test', 'implementation', '1.0').dependsOn(shared11).publish()
        def compileOnlyModule = mavenRepo.module('org.gradle.test', 'compileOnly', '1.0').dependsOn(shared10).publish()

        buildFile << """
apply plugin: 'java'

repositories {
    maven { url '$mavenRepo.uri' }
}

configurations {
    implementationClasspath {
        extendsFrom implementation
    }
    compileOnlyClasspath {
        extendsFrom compileOnly
    }
}

dependencies {
    implementation 'org.gradle.test:implementation:1.0'
    compileOnly 'org.gradle.test:compileOnly:1.0'
}

task checkImplementation {
    def implementationClasspath = configurations.implementationClasspath
    def expectedFiles = provider {
        [file('${normaliseFileSeparators(shared11.artifactFile.path)}'), file('${normaliseFileSeparators(implementationModule.artifactFile.path)}')] as Set
    }
    doLast {
        assert implementationClasspath.files == expectedFiles.get()
    }
}

task checkCompileOnly {
    def compileOnlyClasspath = configurations.compileOnlyClasspath
    def expectedFiles = provider {
        [file('${normaliseFileSeparators(shared10.artifactFile.path)}'), file('${normaliseFileSeparators(compileOnlyModule.artifactFile.path)}')] as Set
    }
    doLast {
        assert compileOnlyClasspath.files == expectedFiles.get()
    }
}

task checkCompileClasspath{
    def compileClasspath = configurations.compileClasspath
    def expectedFiles = provider {
        [file('${normaliseFileSeparators(shared11.artifactFile.path)}'), file('${normaliseFileSeparators(implementationModule.artifactFile.path)}'), file('${normaliseFileSeparators(compileOnlyModule.artifactFile.path)}')] as Set
    }
    doLast {
        assert compileClasspath.files == expectedFiles.get()
    }
}
"""
        expect:
        succeeds('checkImplementation', 'checkCompileOnly', 'checkCompileClasspath')
    }

    def "compile only dependencies from project dependency are non transitive"() {
        given:
        mavenRepo.module('org.gradle.test', 'compileOnly', '1.0').publish()

        and:
        createDirs("projectA", "projectB")
        settingsFile << "include 'projectA', 'projectB'"

        buildFile << """
allprojects {
    apply plugin: 'java-library'

    repositories {
        maven { url '$mavenRepo.uri' }
    }
}

project(':projectA') {
    dependencies {
        compileOnly 'org.gradle.test:compileOnly:1.0'
    }
}

project(':projectB') {
    dependencies {
        api project(':projectA')
    }

    task checkClasspath {
        def compileClasspathFiles = configurations.compileClasspath.files
        def projectAJavaDestDir = project(':projectA').compileJava.destinationDirectory
        doLast {
            assert compileClasspathFiles == [projectAJavaDestDir.get().asFile] as Set
        }
    }
}
"""

        expect:
        succeeds('checkClasspath')
    }

    def "correct configurations for compile only project dependency"() {
        given:
        createDirs("projectA", "projectB")
        settingsFile << "include 'projectA', 'projectB'"

        and:
        buildFile << """
            allprojects {
                apply plugin: 'java-library'
                repositories {
                    maven { url '$mavenRepo.uri' }
                }
            }

            project(':projectA') {}

            project(':projectB') {
                configurations {
                    compileOnlyClasspath {
                        extendsFrom compileOnly
                    }
                    implementationClasspath {
                        extendsFrom implementation
                    }
                }

                dependencies {
                    compileOnly project(':projectA')
                }

                task checkClasspath {
                    def compileClasspathFiles = configurations.compileClasspath.files
                    def runtimeClasspathFiles = configurations.runtimeClasspath.files
                    def compileOnlyClasspathFiles = configurations.compileOnlyClasspath.files
                    def implementationClasspathFiles = configurations.implementationClasspath.files
                    def projectAJavaDir = project(':projectA').compileJava.destinationDirectory
                    def projectAJarArchiveFile = project(':projectA').jar.archiveFile
                    doLast {
                        assert compileClasspathFiles == [projectAJavaDir.get().asFile] as Set
                        assert runtimeClasspathFiles == [] as Set
                        assert compileOnlyClasspathFiles == [projectAJarArchiveFile.get().asFile] as Set
                        assert implementationClasspathFiles == [] as Set
                    }
                }
            }
        """.stripIndent()

        expect:
        succeeds('checkClasspath')
    }

    def "can compile against compile only dependencies for additional source sets"() {
        given:
        file('src/additional/java/Test.java') << """
            import org.apache.commons.logging.Log;
            import org.apache.commons.logging.LogFactory;

            public class Test {
                private static final Log logger = LogFactory.getLog(Test.class);
            }
        """.stripIndent()

        and:
        buildFile << """
            apply plugin: 'java'

            ${mavenCentralRepository()}

            sourceSets {
                additional
            }

            dependencies {
                additionalCompileOnly 'commons-logging:commons-logging:1.2'
            }
        """

        when:
        run('compileAdditionalJava')

        then:
        classFile('java', 'additional', 'Test.class').exists()
    }

}
