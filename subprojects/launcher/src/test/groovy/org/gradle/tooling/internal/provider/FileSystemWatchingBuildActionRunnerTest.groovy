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

package org.gradle.tooling.internal.provider

import org.gradle.api.internal.GradleInternal
import org.gradle.api.internal.StartParameterInternal
import org.gradle.internal.invocation.BuildAction
import org.gradle.internal.invocation.BuildActionRunner
import org.gradle.internal.invocation.BuildController
import org.gradle.internal.service.ServiceRegistry
import org.gradle.internal.vfs.VirtualFileSystem
import org.gradle.internal.watch.vfs.FileSystemWatchingHandler
import spock.lang.Specification
import spock.lang.Unroll

@Unroll
class FileSystemWatchingBuildActionRunnerTest extends Specification {

    def watchingHandler = Mock(FileSystemWatchingHandler)
    def virtualFileSystem = Mock(VirtualFileSystem)
    def startParameter = Mock(StartParameterInternal)
    def buildController = Stub(BuildController) {
        getGradle() >> Stub(GradleInternal) {
            getStartParameter() >> startParameter
            getServices() >> Stub(ServiceRegistry) {
                get(FileSystemWatchingHandler) >> watchingHandler
                get(VirtualFileSystem) >> virtualFileSystem
            }
        }
    }
    def delegate = Mock(BuildActionRunner)
    def buildAction = Mock(BuildAction)

    def "watching virtual file system is informed about watching the file system being #watchFsEnabledString"() {
        _ * startParameter.getSystemPropertiesArgs() >> [:]
        _ * startParameter.isWatchFileSystem() >> watchFsEnabled

        def runner = new FileSystemWatchingBuildActionRunner(delegate)

        when:
        runner.run(buildAction, buildController)
        then:
        1 * watchingHandler.afterBuildStarted(watchFsEnabled)

        then:
        1 * delegate.run(buildAction, buildController)

        then:
        1 * watchingHandler.beforeBuildFinished(watchFsEnabled)

        where:
        watchFsEnabled << [true, false]
        watchFsEnabledString = watchFsEnabled ? "enabled" : "disabled"
    }
}
