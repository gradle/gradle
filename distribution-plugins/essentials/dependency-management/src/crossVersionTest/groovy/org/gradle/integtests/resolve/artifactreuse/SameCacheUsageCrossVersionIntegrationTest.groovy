/*
 * Copyright 2012 the original author or authors.
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


package org.gradle.integtests.resolve.artifactreuse

import org.gradle.api.internal.artifacts.ivyservice.DefaultArtifactCacheMetadata
import org.gradle.integtests.fixtures.IgnoreVersions
import org.gradle.integtests.fixtures.executer.DefaultGradleDistribution
import org.gradle.util.GradleVersion

@IgnoreVersions({ it.artifactCacheLayoutVersion != DefaultArtifactCacheMetadata.CACHE_LAYOUT_VERSION })
class SameCacheUsageCrossVersionIntegrationTest extends AbstractCacheReuseCrossVersionIntegrationTest {
    def "incurs zero remote requests when cache version not upgraded"() {
        given:
        def projectB = mavenHttpRepo.module('org.name', 'projectB', '1.0').publish()
        server.sendSha1Header = false
        buildFile << """
repositories {
    maven { url '${mavenHttpRepo.uri}' }
}
configurations { compile }
dependencies {
    compile 'org.name:projectB:1.0'
}

task retrieve(type: Sync) {
    into 'libs'
    from configurations.compile
}
"""
        and:
        def userHome = file('user-home')

        when:
        projectB.allowAll()

        and:
        version previous withGradleUserHomeDir userHome withTasks 'retrieve' withArguments '-i' run()

        then:
        file('libs').assertHasDescendants('projectB-1.0.jar')
        def snapshot = file('libs/projectB-1.0.jar').snapshot()

        when:
        server.resetExpectations()
        //expect no http requests

        and:
        version current withGradleUserHomeDir userHome withTasks 'retrieve' withArguments '-i' run()

        then:
        file('libs').assertHasDescendants('projectB-1.0.jar')
        file('libs/projectB-1.0.jar').assertContentsHaveNotChangedSince(snapshot)
    }

    static boolean isIgnoredMilestone(DefaultGradleDistribution distribution) {
        def v = distribution.version
        v.snapshot && v.baseVersion.version.equals("5.0") && v < GradleVersion.version("5.0-20181004235847+0000") // 5.0-milestone-1 had a different cache layout version
    }
}
