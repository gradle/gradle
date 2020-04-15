/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.integtests.resolve.locking

import org.gradle.integtests.fixtures.AbstractDependencyResolutionTest
import org.gradle.integtests.fixtures.FeaturePreviewsFixture
import org.gradle.integtests.fixtures.ToBeFixedForInstantExecution
import org.gradle.test.fixtures.plugin.PluginBuilder
import org.gradle.test.fixtures.server.http.MavenHttpPluginRepository
import org.junit.Rule
import spock.lang.Unroll

class UsingLockingOnNonProjectConfigurationsIntegrationTest extends AbstractDependencyResolutionTest {

    def pluginBuilder = new PluginBuilder(file("plugin"))
    def lockfileFixture = new LockfileFixture(testDirectory: testDirectory)

    @Rule
    MavenHttpPluginRepository pluginRepo = MavenHttpPluginRepository.asGradlePluginPortal(executer, mavenRepo)

    @ToBeFixedForInstantExecution(because = ":buildEnvironment")
    @Unroll
    def 'locks build script classpath configuration (unique: #unique)'() {
        given:
        mavenRepo.module('org.foo', 'foo-plugin', '1.0').publish()
        mavenRepo.module('org.foo', 'foo-plugin', '1.1').publish()

        settingsFile << """
rootProject.name = 'foo'
"""
        if (unique) {
            FeaturePreviewsFixture.enableOneLockfilePerProject(settingsFile)
        }
        buildFile << """
buildscript {
    repositories {
        maven {
            url = '$mavenRepo.uri'
        }
    }
    configurations.classpath {
        resolutionStrategy.activateDependencyLocking()
    }
    dependencies {
        classpath 'org.foo:foo-plugin:[1.0,2.0)'
    }
}
"""
        lockfileFixture.createBuildscriptLockfile('classpath', ['org.foo:foo-plugin:1.0'], unique)

        when:
        succeeds 'buildEnvironment'

        then:
        outputContains('org.foo:foo-plugin:[1.0,2.0) -> 1.0')
        outputDoesNotContain('org.foo:foo-plugin:1.1')

        where:
        unique << [true, false]
    }

    @ToBeFixedForInstantExecution(because = ":buildEnvironment")
    def 'locks build script classpath configuration in custom lockfile'() {
        given:
        mavenRepo.module('org.foo', 'foo-plugin', '1.0').publish()
        mavenRepo.module('org.foo', 'foo-plugin', '1.1').publish()

        settingsFile << """
rootProject.name = 'foo'
"""
        FeaturePreviewsFixture.enableOneLockfilePerProject(settingsFile)
        buildFile << """
buildscript {
    repositories {
        maven {
            url = '$mavenRepo.uri'
        }
    }
    dependencyLocking {
        lockFile = file("\$projectDir/gradle/bslock.file")
    }
    configurations.classpath {
        resolutionStrategy.activateDependencyLocking()
    }
    dependencies {
        classpath 'org.foo:foo-plugin:[1.0,2.0)'
    }
}
"""
        def lockFile = testDirectory.file('gradle', 'bslock.file')
        LockfileFixture.createCustomLockfile(lockFile, 'classpath', ['org.foo:foo-plugin:1.0'])

        when:
        succeeds 'buildEnvironment'

        then:
        outputContains('org.foo:foo-plugin:[1.0,2.0) -> 1.0')

        when:
        succeeds 'buildEnvironment', '--write-locks', '--refresh-dependencies'

        then:
        LockfileFixture.verifyCustomLockfile(lockFile, 'classpath', ['org.foo:foo-plugin:1.1'])
    }

    @Unroll
    def 'strict locks on buildscript classpath does not mean strict locks on project (unique: #unique)'() {
        given:
        mavenRepo.module('org.foo', 'foo-plugin', '1.0').publish()
        mavenRepo.module('org.foo', 'foo-plugin', '1.1').publish()

        settingsFile << """
rootProject.name = 'foo'
"""
        if (unique) {
            FeaturePreviewsFixture.enableOneLockfilePerProject(settingsFile)
        }
        buildFile << """
buildscript {
    repositories {
        maven {
            url = '$mavenRepo.uri'
        }
    }
    configurations.classpath {
        resolutionStrategy.activateDependencyLocking()
    }
    dependencies {
        classpath 'org.foo:foo-plugin:[1.0,2.0)'
    }
    dependencyLocking {
        lockMode = LockMode.STRICT
    }
}

repositories {
    maven {
        url = '$mavenRepo.uri'
    }
}

configurations {
    foo {
        resolutionStrategy.activateDependencyLocking()
    }
}

dependencies {
    foo 'org.foo:foo-plugin:1.0'
}

task resolve {
    doLast {
        println configurations.foo.files
    }
}
"""
        lockfileFixture.createBuildscriptLockfile('classpath', ['org.foo:foo-plugin:1.0'], unique)

        when:
        succeeds 'resolve'

        then:
        outputContains('foo-plugin-1.0.jar')

        where:
        unique << [true, false]
    }

    @Unroll
    def 'strict lock on build script classpath configuration causes build to fail when no lockfile present (unique: #unique)'() {
        given:
        settingsFile << """
rootProject.name = 'foo'
"""
        if (unique) {
            FeaturePreviewsFixture.enableOneLockfilePerProject(settingsFile)
        }
        buildFile << """
buildscript {
    repositories {
        maven {
            url = '$mavenRepo.uri'
        }
    }
    dependencyLocking {
        lockMode = LockMode.STRICT
    }
    configurations.classpath {
        resolutionStrategy.activateDependencyLocking()
    }
}
"""

        when:
        fails 'buildEnvironment'

        then:
        failureHasCause("Locking strict mode:")

        where:
        unique << [true, false]
    }

    @ToBeFixedForInstantExecution
    @Unroll
    def 'locks build script classpath combined with plugins (unique: #unique)'() {
        given:
        addPlugin()
        mavenRepo.module('org.foo', 'foo-plugin', '1.0').publish()
        mavenRepo.module('org.foo', 'foo-plugin', '1.1').publish()

        settingsFile << """
pluginManagement {
    repositories {
        maven {
            url '$pluginRepo.uri'
        }
    }
}
rootProject.name = 'foo-plugin'
"""
        if (unique) {
            FeaturePreviewsFixture.enableOneLockfilePerProject(settingsFile)
        }
        buildFile << """
buildscript {
    repositories {
        maven {
            url = '$mavenRepo.uri'
        }
    }
    configurations.classpath {
        resolutionStrategy.activateDependencyLocking()
    }
    dependencies {
        classpath 'org.foo:foo-plugin:[1.0,2.0)'
    }
}

plugins {
  id 'bar.plugin' version '1.0'
}
"""
        lockfileFixture.createBuildscriptLockfile('classpath', ['org.foo:foo-plugin:1.0', 'org.bar:bar-plugin:1.0', 'bar.plugin:bar.plugin.gradle.plugin:1.0'], unique)

        when:
        succeeds 'buildEnvironment'

        then:
        outputContains('org.foo:foo-plugin:[1.0,2.0) -> 1.0')
        outputDoesNotContain('org.foo:foo-plugin:1.1')

        where:
        unique << [true, false]
    }

    @ToBeFixedForInstantExecution
    @Unroll
    def 'creates lock file for build script classpath (unique: #unique)'() {
        given:
        addPlugin()
        mavenRepo.module('org.foo', 'foo-plugin', '1.0').publish()
        mavenRepo.module('org.foo', 'foo-plugin', '1.1').publish()

        settingsFile << """
pluginManagement {
    repositories {
        maven {
            url '$pluginRepo.uri'
        }
    }
}
rootProject.name = 'foo-plugin'
"""
        if (unique) {
            FeaturePreviewsFixture.enableOneLockfilePerProject(settingsFile)
        }
        buildFile << """
buildscript {
    repositories {
        maven {
            url = '$mavenRepo.uri'
        }
    }
    configurations.classpath {
        resolutionStrategy.activateDependencyLocking()
    }
    dependencies {
        classpath 'org.foo:foo-plugin:[1.0,2.0)'
    }
}

plugins {
  id 'bar.plugin' version '1.0'
}
"""

        when:
        succeeds 'buildEnvironment', '--write-locks'

        then:
        lockfileFixture.verifyBuildscriptLockfile('classpath', ['org.foo:foo-plugin:1.1', 'org.bar:bar-plugin:1.0', 'bar.plugin:bar.plugin.gradle.plugin:1.0'], unique)

        where:
        unique << [true, false]
    }

    @Unroll
    def 'fails to resolve if lock state present but no dependencies remain (unique: #unique)'() {
        given:

        settingsFile << """
rootProject.name = 'foo'
"""
        if (unique) {
            FeaturePreviewsFixture.enableOneLockfilePerProject(settingsFile)
        }

        buildFile << """
buildscript {
    configurations.classpath {
        resolutionStrategy.activateDependencyLocking()
    }
}
"""
        lockfileFixture.createBuildscriptLockfile('classpath', ['org.foo:foo-plugin:1.0'], unique)

        when:
        fails 'buildEnvironment'

        then:
        failureCauseContains('Did not resolve \'org.foo:foo-plugin:1.0\' which is part of the dependency lock state')

        where:
        unique << [true, false]
    }

    @ToBeFixedForInstantExecution
    @Unroll
    def 'same name buildscript and project configurations result in different lock files (unique: #unique)'() {
        given:
        mavenRepo.module('org.foo', 'foo-plugin', '1.0').publish()
        mavenRepo.module('org.foo', 'foo-plugin', '1.1').publish()
        mavenRepo.module('org.foo', 'foo', '1.0').publish()
        mavenRepo.module('org.foo', 'foo', '1.1').publish()

        settingsFile << """
rootProject.name = 'foo'
"""
        if (unique) {
            FeaturePreviewsFixture.enableOneLockfilePerProject(settingsFile)
        }
        buildFile << """
buildscript {
    repositories {
        maven {
            url = '$mavenRepo.uri'
        }
    }
    configurations.classpath {
        resolutionStrategy.activateDependencyLocking()
    }
    dependencies {
        classpath 'org.foo:foo-plugin:[1.0,2.0)'
    }
}
repositories {
    maven {
        url = '$mavenRepo.uri'
    }
}
configurations {
    classpath {
        resolutionStrategy.activateDependencyLocking()
    }
}
dependencies {
    classpath 'org.foo:foo:[1.0,2.0)'
}
"""
        when:
        succeeds 'buildEnvironment', 'dependencies', '--write-locks'

        then:
        lockfileFixture.verifyBuildscriptLockfile('classpath', ['org.foo:foo-plugin:1.1'], unique)
        lockfileFixture.verifyLockfile('classpath', ['org.foo:foo:1.1'], unique)

        where:
        unique << [true, false]
    }

    def addPlugin() {
        pluginBuilder.addPlugin("System.out.println(\"Hello World\");", 'bar.plugin')
        pluginBuilder.publishAs('org.bar', 'bar-plugin', '1.0', pluginRepo, executer).allowAll()
    }

}
