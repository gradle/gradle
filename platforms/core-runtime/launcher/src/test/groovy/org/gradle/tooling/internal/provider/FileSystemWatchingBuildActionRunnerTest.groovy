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

import org.gradle.api.internal.StartParameterInternal
import org.gradle.api.internal.changedetection.state.FileHasherStatistics
import org.gradle.deployment.internal.Deployment
import org.gradle.deployment.internal.DeploymentRegistryInternal
import org.gradle.initialization.layout.BuildLayout
import org.gradle.internal.buildoption.DefaultInternalOptions
import org.gradle.internal.buildtree.BuildActionRunner
import org.gradle.internal.buildtree.BuildTreeLifecycleController
import org.gradle.internal.file.StatStatistics
import org.gradle.internal.invocation.BuildAction
import org.gradle.internal.operations.BuildOperationProgressEventEmitter
import org.gradle.internal.operations.BuildOperationRunner
import org.gradle.internal.snapshot.impl.DirectorySnapshotterStatistics
import org.gradle.internal.watch.options.FileSystemWatchingSettingsFinalizedProgressDetails
import org.gradle.internal.watch.registry.WatchMode
import org.gradle.internal.watch.vfs.BuildLifecycleAwareVirtualFileSystem
import org.gradle.internal.watch.vfs.VfsLogging
import org.gradle.internal.watch.vfs.WatchLogging
import spock.lang.Specification

class FileSystemWatchingBuildActionRunnerTest extends Specification {

    def watchingHandler = Mock(BuildLifecycleAwareVirtualFileSystem)
    List<Deployment> deployments = []
    def deploymentRegistry = Stub(DeploymentRegistryInternal) {
        getRunningDeployments() >> deployments
    }
    def startParameter = Stub(StartParameterInternal)
    def buildOperationRunner = Mock(BuildOperationRunner)
    def buildLayout = Mock(BuildLayout)
    def buildController = Stub(BuildTreeLifecycleController)
    def delegate = Mock(BuildActionRunner)
    def buildAction = Stub(BuildAction)
    def buildOperationProgressEventEmitter = Mock(BuildOperationProgressEventEmitter)

    def runner = new FileSystemWatchingBuildActionRunner(
        buildOperationProgressEventEmitter,
        watchingHandler,
        deploymentRegistry,
        Stub(StatStatistics.Collector),
        Stub(FileHasherStatistics.Collector),
        Stub(DirectorySnapshotterStatistics.Collector),
        buildLayout,
        buildOperationRunner,
        delegate,
        new DefaultInternalOptions([:]))

    def setup() {
        _ * startParameter.getSystemPropertiesArgs() >> [:]
        _ * buildAction.startParameter >> startParameter
    }

    def "watching virtual file system is informed about watching the file system being #watchMode.description (VFS logging: #vfsLogging, watch logging: #watchLogging)"() {
        _ * startParameter.watchFileSystemMode >> watchMode
        _ * startParameter.projectCacheDir >> null
        _ * startParameter.isWatchFileSystemDebugLogging() >> (watchLogging == WatchLogging.DEBUG)
        _ * startParameter.isVfsVerboseLogging() >> (vfsLogging == VfsLogging.VERBOSE)

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

    def "watching enabled by default is disabled when project cache dir is specified with different fs"() {
        _ * startParameter.watchFileSystemMode >> WatchMode.DEFAULT
        _ * startParameter.projectCacheDir >> new File(".")
        _ * buildLayout.rootDirectory >> null

        when:
        runner.run(buildAction, buildController)

        then:
        1 * buildLayout.rootDirectory

        then:
        1 * watchingHandler.afterBuildStarted(WatchMode.DISABLED, _, _, buildOperationRunner)

        then:
        1 * buildOperationProgressEventEmitter.emitNowForCurrent({ FileSystemWatchingSettingsFinalizedProgressDetails details -> !details.enabled })

        then:
        1 * delegate.run(buildAction, buildController)

        then:
        1 * watchingHandler.beforeBuildFinished(WatchMode.DISABLED, _, _, buildOperationRunner, _)

        then:
        0 * _
    }

    def "force-enables watching when #description"() {
        _ * startParameter.watchFileSystemMode >> WatchMode.DEFAULT
        _ * startParameter.projectCacheDir >> null
        _ * startParameter.continuous >> isContinuousBuild
        if (hasDeployment) {
            deployments.add(Stub(Deployment))
        }

        when:
        runner.run(buildAction, buildController)

        then:
        1 * watchingHandler.afterBuildStarted(WatchMode.ENABLED, _, _, buildOperationRunner) >> true

        then:
        1 * buildOperationProgressEventEmitter.emitNowForCurrent({ FileSystemWatchingSettingsFinalizedProgressDetails details -> details.enabled })

        then:
        1 * delegate.run(buildAction, buildController)

        then:
        1 * watchingHandler.beforeBuildFinished(WatchMode.ENABLED, _, _, buildOperationRunner, _)

        then:
        0 * _

        where:
        description               | hasDeployment | isContinuousBuild
        "using continuous build"  | false         | true
        "a deployment is running" | true          | false
    }

    def "fails when #description and file system watching is disabled"() {
        _ * startParameter.watchFileSystemMode >> WatchMode.DISABLED
        _ * startParameter.projectCacheDir >> null
        _ * startParameter.continuous >> isContinuousBuild
        if (hasDeployment) {
            deployments.add(Stub(Deployment))
        }

        when:
        runner.run(buildAction, buildController)

        then:
        def e = thrown(IllegalStateException)
        e.message == "Continuous build does not work when file system watching is disabled"

        where:
        description               | hasDeployment | isContinuousBuild
        "using continuous build"  | false         | true
        "a deployment is running" | true          | false
    }
}
