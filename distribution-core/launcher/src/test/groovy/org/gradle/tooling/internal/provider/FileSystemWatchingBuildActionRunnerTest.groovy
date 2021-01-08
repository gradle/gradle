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
import org.gradle.api.internal.changedetection.state.FileHasherStatistics
import org.gradle.internal.file.StatStatistics
import org.gradle.internal.invocation.BuildAction
import org.gradle.internal.invocation.BuildActionRunner
import org.gradle.internal.invocation.BuildController
import org.gradle.internal.operations.BuildOperationProgressEventEmitter
import org.gradle.internal.operations.BuildOperationRunner
import org.gradle.internal.service.ServiceRegistry
import org.gradle.internal.snapshot.impl.DirectorySnapshotterStatistics
import org.gradle.internal.watch.options.FileSystemWatchingSettingsFinalizedProgressDetails
import org.gradle.internal.watch.vfs.BuildLifecycleAwareVirtualFileSystem
import org.gradle.internal.watch.vfs.VfsLogging
import org.gradle.internal.watch.vfs.WatchLogging
import org.gradle.internal.watch.vfs.WatchMode
import spock.lang.Specification
import spock.lang.Unroll

@Unroll
class FileSystemWatchingBuildActionRunnerTest extends Specification {

    def watchingHandler = Mock(BuildLifecycleAwareVirtualFileSystem)
    def startParameter = Stub(StartParameterInternal)
    def buildOperationRunner = Mock(BuildOperationRunner)
    def buildController = Stub(BuildController) {
        getGradle() >> Stub(GradleInternal) {
            getStartParameter() >> startParameter
            getServices() >> Stub(ServiceRegistry) {
                get(BuildLifecycleAwareVirtualFileSystem) >> watchingHandler
                get(BuildOperationRunner) >> buildOperationRunner
                get(FileHasherStatistics.Collector) >> Stub(FileHasherStatistics.Collector)
                get(StatStatistics.Collector) >> Stub(StatStatistics.Collector)
                get(DirectorySnapshotterStatistics.Collector) >> Stub(DirectorySnapshotterStatistics.Collector)
            }
        }
    }
    def delegate = Mock(BuildActionRunner)
    def buildAction = Mock(BuildAction)
    def buildOperationProgressEventEmitter = Mock(BuildOperationProgressEventEmitter)

    def "watching virtual file system is informed about watching the file system being #watchMode.description (VFS logging: #vfsLogging, watch logging: #watchLogging)"() {
        _ * startParameter.getSystemPropertiesArgs() >> [:]
        _ * startParameter.watchFileSystemMode >> watchMode
        _ * startParameter.isWatchFileSystemDebugLogging() >> (watchLogging == WatchLogging.DEBUG)
        _ * startParameter.isVfsVerboseLogging() >> (vfsLogging == VfsLogging.VERBOSE)
        _ * startParameter.isVfsDebugLogging() >> false

        def runner = new FileSystemWatchingBuildActionRunner(buildOperationProgressEventEmitter, delegate)

        when:
        runner.run(buildAction, buildController)
        then:
        1 * watchingHandler.afterBuildStarted(watchMode, vfsLogging, watchLogging, buildOperationRunner) >> actuallyEnabled

        then:
        1 * buildOperationProgressEventEmitter.emitNowForCurrent({ FileSystemWatchingSettingsFinalizedProgressDetails details -> details.enabled == actuallyEnabled })

        then:
        1 * delegate.run(buildAction, buildController)

        then:
        1 * watchingHandler.beforeBuildFinished(watchMode, vfsLogging, watchLogging, buildOperationRunner, _)

        then:
        0 * _

        where:
        watchMode          | vfsLogging         | watchLogging        | actuallyEnabled
        WatchMode.DEFAULT  | VfsLogging.VERBOSE | WatchLogging.NORMAL | true
        WatchMode.DEFAULT  | VfsLogging.NORMAL  | WatchLogging.NORMAL | false
        WatchMode.DEFAULT  | VfsLogging.VERBOSE | WatchLogging.DEBUG  | false
        WatchMode.DEFAULT  | VfsLogging.NORMAL  | WatchLogging.DEBUG  | true
        WatchMode.ENABLED  | VfsLogging.VERBOSE | WatchLogging.NORMAL | true
        WatchMode.ENABLED  | VfsLogging.NORMAL  | WatchLogging.NORMAL | true
        WatchMode.ENABLED  | VfsLogging.VERBOSE | WatchLogging.DEBUG  | true
        WatchMode.ENABLED  | VfsLogging.NORMAL  | WatchLogging.DEBUG  | true
        WatchMode.DISABLED | VfsLogging.NORMAL  | WatchLogging.NORMAL | false
        WatchMode.DISABLED | VfsLogging.NORMAL  | WatchLogging.DEBUG  | false
    }
}
