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

package org.gradle.plugins.ide.idea

import org.gradle.integtests.fixtures.ToBeFixedForConfigurationCache
import org.gradle.plugins.ide.AbstractIdeIntegrationTest
import org.junit.Test

class IdeaDependencyLockingIntegrationTest extends AbstractIdeIntegrationTest {

    @Test
    @ToBeFixedForConfigurationCache
    void "does not break when lockfile is out of date"() {
        //given
        def mvnRepo = maven(file("repo"))
        mvnRepo.module("groupOne", "artifactTwo").publish()
        mvnRepo.module("groupOne", "artifactTwo", "1.1").publish()
        mvnRepo.module("groupOne", "artifactTwo", "2.0").publish()

        file('gradle/dependency-locks/compileClasspath.lockfile') << 'groupOne:artifactTwo:1.1'

        //when
        runIdeaTask """
apply plugin: 'java'
apply plugin: 'idea'

repositories {
    maven { url = "${mvnRepo.uri}" }
}

configurations {
    compileClasspath {
        resolutionStrategy.activateDependencyLocking()
    }
}

dependencies {
    implementation 'groupOne:artifactTwo:[2.0,3.0)'
}
"""
        def content = getFile([print : true], 'root.iml').text

        //then
        assert content.count("artifactTwo-2.0.jar") == 1
        assert content.count("unresolved dependency - groupOne artifactTwo") >= 1
    }

    @Test
    @ToBeFixedForConfigurationCache
    void "does not break when extra dependency not in lockfile is defined"() {
        //given
        def mvnRepo = maven(file("repo"))
        mvnRepo.module("groupOne", "artifactOne").publish()
        mvnRepo.module("groupOne", "artifactOne", "1.1").publish()
        mvnRepo.module("groupOne", "artifactTwo", "2.0").publish()

        file('gradle/dependency-locks/compileClasspath.lockfile') << 'groupOne:artifactOne:1.1'

        //when
        runIdeaTask """
apply plugin: 'java'
apply plugin: 'idea'

repositories {
    maven { url = "${mvnRepo.uri}" }
}

configurations {
    compileClasspath {
        resolutionStrategy.activateDependencyLocking()
    }
}

dependencies {
    implementation 'groupOne:artifactOne:[1.0,2.0)'
    implementation 'groupOne:artifactTwo:[2.0,3.0)'
}
"""
        def content = getFile([print : true], 'root.iml').text

        //then
        assert content.count("artifactOne-1.1.jar") == 1
        assert content.count("artifactTwo-2.0.jar") == 1
        assert content.count("unresolved dependency - groupOne artifactTwo 2.0") == 1
    }

}
