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

repositories {
    jcenter()
}

dependencies {
    compileOnly 'commons-logging:commons-logging:1.2'
}
"""

        when:
        run('compileJava')

        then:
        file('build/classes/main/Test.class').exists()
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

repositories {
    jcenter()
}

dependencies {
    compileOnly 'commons-logging:commons-logging:1.2'
}
"""

        when:
        def failure = fails('compileTestJava')

        then:
        failure.assertHasCause("Compilation failed; see the compiler error output for details.")
        failure.error.contains("error: package org.apache.commons.logging does not exist")
    }

    def "compile only dependencies not included in runtime classpath"() {
        given:
        def compileModule = mavenRepo.module('org.gradle.test', 'compile', '1.2').publish()
        def compileOnlyModule = mavenRepo.module('org.gradle.test', 'compileOnly', '1.0').publish()
        def runtimeModule = mavenRepo.module('org.gradle.test', 'runtime', '1.1').publish()

        buildFile << """
apply plugin: 'java'

repositories {
    maven { url '$mavenRepo.uri' }
}

dependencies {
    compile 'org.gradle.test:compile:1.2'
    compileOnly 'org.gradle.test:compileOnly:1.0'
    runtime 'org.gradle.test:runtime:1.1'
}

task checkCompile << {
    assert configurations.compile.files == [file('${compileModule.artifactFile}')] as Set
}

task checkCompileOnly << {
    assert configurations.compileOnly.files == [file('${compileModule.artifactFile}'), file('${compileOnlyModule.artifactFile}')] as Set
}

task checkRuntime << {
    assert configurations.runtime.files == [file('${compileModule.artifactFile}'), file('${runtimeModule.artifactFile}')] as Set
}
"""

        expect:
        succeeds('checkCompile', 'checkCompileOnly', 'checkRuntime')
    }

    def "conflicts resolved between compile and compile only dependencies"() {
        given:
        def shared10 = mavenRepo.module('org.gradle.test', 'shared', '1.0').publish()
        def shared11 = mavenRepo.module('org.gradle.test', 'shared', '1.1').publish()
        def compileModule = mavenRepo.module('org.gradle.test', 'compile', '1.0').dependsOn(shared11).publish()
        def compileOnlyModule = mavenRepo.module('org.gradle.test', 'compileOnly', '1.0').dependsOn(shared10).publish()

        buildFile << """
apply plugin: 'java'

repositories {
    maven { url '$mavenRepo.uri' }
}

dependencies {
    compile 'org.gradle.test:compile:1.0'
    compileOnly 'org.gradle.test:compileOnly:1.0'
}

task checkCompile << {
    assert configurations.compile.files == [file('${shared11.artifactFile}'), file('${compileModule.artifactFile}')] as Set
}

task checkCompileOnly << {
    assert configurations.compileOnly.files == [file('${shared11.artifactFile}'), file('${compileModule.artifactFile}'), file('${compileOnlyModule.artifactFile}')] as Set
}
"""
        expect:
        succeeds('checkCompile', 'checkCompileOnly')
    }

    def "compile only dependencies from project dependency are non transitive"() {
        given:
        mavenRepo.module('org.gradle.test', 'compileOnly', '1.0').publish()

        and:
        settingsFile << "include 'projectA', 'projectB'"

        buildFile << """
allprojects {
    apply plugin: 'java'

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
        compile project(':projectA')
    }

    task checkClasspath << {
        assert configurations.compile.files == [project(':projectA').jar.archivePath] as Set
    }
}
"""

        expect:
        succeeds('checkClasspath')
    }
}
