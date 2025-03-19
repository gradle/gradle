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

package org.gradle.plugins.ide.eclipse

import org.gradle.integtests.fixtures.ToBeFixedForConfigurationCache
import org.junit.Test

class EclipseDependencyLockingIntegrationTest extends AbstractEclipseIntegrationTest {

    @Test
    @ToBeFixedForConfigurationCache
    void "does not break when lockfile is out of date"() {
        //given
        def mvnRepo = maven(file("repo"))
        mvnRepo.module("groupOne", "artifactTwo").publish()
        mvnRepo.module("groupOne", "artifactTwo", "1.1").publish()
        def repoJar = mvnRepo.module("groupOne", "artifactTwo", "2.0").publish().artifactFile

        file('gradle/dependency-locks/compileClasspath.lockfile') << 'groupOne:artifactTwo:1.1'

        //when
        runEclipseTask """
apply plugin: 'java'
apply plugin: 'eclipse'

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

        //then
        def libraries = classpath.libs
        assert libraries.size() == 3
        libraries[0].assertHasJar(repoJar)
    }

    @Test
    @ToBeFixedForConfigurationCache
    void "does not break when extra dependency not in lockfile is defined"() {
        //given
        def mvnRepo = maven(file("repo"))
        mvnRepo.module("groupOne", "artifactOne").publish()
        def artifactOne = mvnRepo.module("groupOne", "artifactOne", "1.1").publish().artifactFile
        def artifactTwo = mvnRepo.module("groupOne", "artifactTwo", "2.0").publish().artifactFile

        file('gradle/dependency-locks/compileClasspath.lockfile') << 'groupOne:artifactOne:1.1'

        //when
        runEclipseTask """
apply plugin: 'java'
apply plugin: 'eclipse'

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

        //then
        def libraries = classpath.libs
        assert libraries.size() == 3
        libraries[0].assertHasJar(artifactOne)
        libraries[1].assertHasJar(artifactTwo)
    }

}
