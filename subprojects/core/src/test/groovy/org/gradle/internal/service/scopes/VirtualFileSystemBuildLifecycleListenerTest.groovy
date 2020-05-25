/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.internal.service.scopes

import org.gradle.api.Action
import org.gradle.api.initialization.IncludedBuild
import org.gradle.api.internal.GradleInternal
import org.gradle.api.internal.StartParameterInternal
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.invocation.Gradle
import org.gradle.internal.watch.vfs.WatchingAwareVirtualFileSystem
import spock.lang.Specification

class VirtualFileSystemBuildLifecycleListenerTest extends Specification {
    def vfs = Mock(WatchingAwareVirtualFileSystem)
    def listener = new VirtualFileSystemBuildLifecycleListener(vfs)
    def gradle = Mock(GradleInternal)
    def startParameter = Mock(StartParameterInternal)
    def rootProject = Mock(ProjectInternal)

    def "updates root directories of included builds"() {
        def rootProjectDir = new File("rootProject")
        def includedBuildsRootDirs = [new File("includedBuild1"), new File("includedBuild2")]
        def includedBuilds = includedBuildsRootDirs.collect { projectDir ->
            Stub(IncludedBuild) {
                getProjectDir() >> projectDir
            }
        }

        when:
        listener.afterStart(gradle)

        then:
        1 * gradle.startParameter >> startParameter
        _ * startParameter.isWatchFileSystem() >> true
        _ * startParameter.systemPropertiesArgs >> [:]

        1 * vfs.afterBuildStarted(true)

        then:
        1 * gradle.projectsLoaded(_) >> { Action<? super Gradle> action ->
            action.execute(gradle)
        }
        1 * gradle.rootProject >> rootProject
        1 * rootProject.projectDir >> rootProjectDir
        1 * gradle.includedBuilds >> includedBuilds

        then:
        1 * vfs.updateProjectRootDirectories([rootProjectDir] + includedBuildsRootDirs)
        0 * _
    }

}
