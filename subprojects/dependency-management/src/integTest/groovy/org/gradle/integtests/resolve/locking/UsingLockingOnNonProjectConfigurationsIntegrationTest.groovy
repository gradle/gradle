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
import org.gradle.test.fixtures.plugin.PluginBuilder
import org.gradle.test.fixtures.server.http.MavenHttpPluginRepository
import org.junit.Rule

class UsingLockingOnNonProjectConfigurationsIntegrationTest extends AbstractDependencyResolutionTest {

    def pluginBuilder = new PluginBuilder(file("plugin"))

    @Rule
    MavenHttpPluginRepository pluginRepo = MavenHttpPluginRepository.asGradlePluginPortal(executer, mavenRepo)

    def 'locks build script classpath configuration'() {
        given:
        mavenRepo.module('org.foo', 'foo-plugin', '1.0').publish()
        mavenRepo.module('org.foo', 'foo-plugin', '1.1').publish()

        settingsFile << """
rootProject.name = 'foo'
"""
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
        def lockFile = new LockfileFixture(testDirectory: testDirectory)
        lockFile.createLockfile('buildscript-classpath', ['org.foo:foo-plugin:1.0'])

        when:
        succeeds 'buildEnvironment'

        then:
        outputContains('org.foo:foo-plugin:[1.0,2.0) -> 1.0')
        outputDoesNotContain('org.foo:foo-plugin:1.1')
    }

    def 'locks build script classpath combined with plugins'() {
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
        def lockFile = new LockfileFixture(testDirectory: testDirectory)
        lockFile.createLockfile('buildscript-classpath', ['org.foo:foo-plugin:1.0', 'org.bar:bar-plugin:1.0', 'bar.plugin:bar.plugin.gradle.plugin:1.0'])

        when:
        succeeds 'buildEnvironment'

        then:
        outputContains('org.foo:foo-plugin:[1.0,2.0) -> 1.0')
        outputDoesNotContain('org.foo:foo-plugin:1.1')
    }

    def 'creates lock file for build script classpath'() {
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
        def lockFile = new LockfileFixture(testDirectory: testDirectory)
        lockFile.verifyLockfile('buildscript-classpath', ['org.foo:foo-plugin:1.1', 'org.bar:bar-plugin:1.0', 'bar.plugin:bar.plugin.gradle.plugin:1.0'])
    }

    def 'fails to resolve if lock state present but no dependencies remain'() {
        given:

        settingsFile << """
rootProject.name = 'foo'
"""

        buildFile << """
buildscript {
    configurations.classpath {
        resolutionStrategy.activateDependencyLocking()
    }
}
"""
        def lockFile = new LockfileFixture(testDirectory: testDirectory)
        lockFile.createLockfile('buildscript-classpath', ['org.foo:foo-plugin:1.0'])

        when:
        fails 'buildEnvironment'

        then:
        failureCauseContains('Did not resolve \'org.foo:foo-plugin:1.0\' which is part of the dependency lock state')
    }

    def 'same name buildscript and project configurations result in different lock files'() {
        given:
        def lockFile = new LockfileFixture(testDirectory: testDirectory)
        mavenRepo.module('org.foo', 'foo-plugin', '1.0').publish()
        mavenRepo.module('org.foo', 'foo-plugin', '1.1').publish()
        mavenRepo.module('org.foo', 'foo', '1.0').publish()
        mavenRepo.module('org.foo', 'foo', '1.1').publish()

        settingsFile << """
rootProject.name = 'foo'
"""
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
        lockFile.verifyLockfile('buildscript-classpath', ['org.foo:foo-plugin:1.1'])
        lockFile.verifyLockfile('classpath', ['org.foo:foo:1.1'])
    }

    def addPlugin() {
        pluginBuilder.addPlugin("System.out.println(\"Hello World\");", 'bar.plugin')
        pluginBuilder.publishAs('org.bar', 'bar-plugin', '1.0', pluginRepo, executer).allowAll()
    }

}
