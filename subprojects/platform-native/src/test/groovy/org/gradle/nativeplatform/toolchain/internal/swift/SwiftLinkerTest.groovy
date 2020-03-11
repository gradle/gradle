/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.nativeplatform.toolchain.internal.swift

import org.gradle.internal.Actions
import org.gradle.internal.operations.BuildOperationExecutor
import org.gradle.internal.operations.BuildOperationQueue
import org.gradle.internal.operations.logging.BuildOperationLogger
import org.gradle.internal.os.OperatingSystem
import org.gradle.internal.work.WorkerLeaseService
import org.gradle.nativeplatform.internal.BundleLinkerSpec
import org.gradle.nativeplatform.internal.LinkerSpec
import org.gradle.nativeplatform.internal.SharedLibraryLinkerSpec
import org.gradle.nativeplatform.platform.NativePlatform
import org.gradle.nativeplatform.platform.internal.DefaultNativePlatform
import org.gradle.nativeplatform.platform.internal.DefaultOperatingSystem
import org.gradle.nativeplatform.toolchain.internal.CommandLineToolContext
import org.gradle.nativeplatform.toolchain.internal.CommandLineToolInvocation
import org.gradle.nativeplatform.toolchain.internal.CommandLineToolInvocationWorker
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.test.fixtures.work.TestWorkerLeaseService
import org.gradle.util.UsesNativeServices
import org.junit.Rule
import spock.lang.Specification

@UsesNativeServices
class SwiftLinkerTest extends Specification {
    public static final String LOG_LOCATION = "<log location>"
    @Rule final TestNameTestDirectoryProvider tmpDirProvider = new TestNameTestDirectoryProvider(getClass())

    def operationLogger =  Mock(BuildOperationLogger)
    def executable = new File("executable")
    def invocationContext = Mock(CommandLineToolContext)
    def invocation = Mock(CommandLineToolInvocation)
    CommandLineToolInvocationWorker commandLineTool = Mock(CommandLineToolInvocationWorker)
    BuildOperationExecutor buildOperationExecutor = Mock(BuildOperationExecutor)
    BuildOperationQueue queue = Mock(BuildOperationQueue)
    WorkerLeaseService workerLeaseService = new TestWorkerLeaseService()

    SwiftLinker linker = new SwiftLinker(buildOperationExecutor, commandLineTool, invocationContext, workerLeaseService)

    def "ignores install name for all major operating system"() {
        given:
        def testDir = tmpDirProvider.testDirectory
        def outputFile = testDir.file("output/lib")

        final expectedArgs = [
            "-emit-library",
            "-o", outputFile.absolutePath,
            testDir.file("one.o").absolutePath].flatten()

        when:
        NativePlatform platform = Mock(NativePlatform)
        platform.getOperatingSystem() >> new DefaultOperatingSystem(operatingSystem.name, operatingSystem)

        LinkerSpec spec = Mock(SharedLibraryLinkerSpec)
        spec.getSystemArgs() >> []
        spec.getArgs() >> []
        spec.getOutputFile() >> outputFile
        spec.getLibraries() >> []
        spec.getLibraryPath() >> []
        spec.getInstallName() >> "installName"
        spec.getTargetPlatform() >> platform
        spec.getObjectFiles() >> [testDir.file("one.o")]
        spec.getOperationLogger() >> operationLogger

        and:
        linker.execute(spec)

        then:
        1 * operationLogger.getLogLocation() >> LOG_LOCATION
        1 * buildOperationExecutor.runAll(commandLineTool, _) >> { worker, action -> action.execute(queue) }
        1 * invocationContext.getArgAction() >> Actions.doNothing()
        1 * invocationContext.createInvocation("linking lib", outputFile.parentFile, expectedArgs, operationLogger) >> invocation
        1 * queue.add(invocation)
        1 * queue.setLogLocation(LOG_LOCATION)
        0 * _

        where:
        operatingSystem << [OperatingSystem.WINDOWS, OperatingSystem.MAC_OS, OperatingSystem.LINUX]
    }

    def "links all object files in a single execution"() {
        given:
        def testDir = tmpDirProvider.testDirectory
        def outputFile = testDir.file("output/lib")

        final expectedArgs = [
                "-sys1", "-sys2",
                "-emit-library",
                "-o", outputFile.absolutePath,
                testDir.file("one.o").absolutePath,
                testDir.file("two.o").absolutePath,
                "-arg1", "-arg2"].flatten()

        when:
        LinkerSpec spec = Mock(SharedLibraryLinkerSpec)
        spec.getSystemArgs() >> ['-sys1', '-sys2']
        spec.getArgs() >> ['-arg1', '-arg2']
        spec.getOutputFile() >> outputFile
        spec.getLibraries() >> []
        spec.getLibraryPath() >> []
        spec.getTargetPlatform() >> new DefaultNativePlatform("default")
        spec.getObjectFiles() >> [testDir.file("one.o"), testDir.file("two.o")]
        spec.getOperationLogger() >> operationLogger

        and:
        linker.execute(spec)

        then:
        1 * operationLogger.getLogLocation() >> LOG_LOCATION
        1 * buildOperationExecutor.runAll(commandLineTool, _) >> { worker, action -> action.execute(queue) }
        1 * invocationContext.getArgAction() >> Actions.doNothing()
        1 * invocationContext.createInvocation("linking lib", outputFile.parentFile, expectedArgs, operationLogger) >> invocation
        1 * queue.add(invocation)
        1 * queue.setLogLocation(LOG_LOCATION)
        0 * _
    }

    def "use the emit-library flag when linking shared library"() {
        given:
        def testDir = tmpDirProvider.testDirectory
        def outputFile = testDir.file("output/lib")

        final expectedArgs = [
            "-emit-library",
            "-o", outputFile.absolutePath,
            testDir.file("one.o").absolutePath].flatten()

        when:
        LinkerSpec spec = mockLinkerSpec(SharedLibraryLinkerSpec, outputFile, [testDir.file("one.o")])
        linker.execute(spec)

        then:
        1 * operationLogger.getLogLocation() >> LOG_LOCATION
        1 * buildOperationExecutor.runAll(commandLineTool, _) >> { worker, action -> action.execute(queue) }
        1 * invocationContext.getArgAction() >> Actions.doNothing()
        1 * invocationContext.createInvocation("linking lib", outputFile.parentFile, expectedArgs, operationLogger) >> invocation
        1 * queue.add(invocation)
        1 * queue.setLogLocation(LOG_LOCATION)
        0 * _
    }

    def "use the emit-executable flag when linking executable"() {
        given:
        def testDir = tmpDirProvider.testDirectory
        def outputFile = testDir.file("output/lib")

        final expectedArgs = [
            "-emit-executable",
            "-o", outputFile.absolutePath,
            testDir.file("one.o").absolutePath].flatten()

        when:
        LinkerSpec spec = mockLinkerSpec(LinkerSpec, outputFile, [testDir.file("one.o")])
        linker.execute(spec)

        then:
        1 * operationLogger.getLogLocation() >> LOG_LOCATION
        1 * buildOperationExecutor.runAll(commandLineTool, _) >> { worker, action -> action.execute(queue) }
        1 * invocationContext.getArgAction() >> Actions.doNothing()
        1 * invocationContext.createInvocation("linking lib", outputFile.parentFile, expectedArgs, operationLogger) >> invocation
        1 * queue.add(invocation)
        1 * queue.setLogLocation(LOG_LOCATION)
        0 * _
    }

    def "pass the bundle flag to the linker when linking bundle"() {
        given:
        def testDir = tmpDirProvider.testDirectory
        def outputFile = testDir.file("output/lib")

        final expectedArgs = [
            "-Xlinker", "-bundle",
            "-o", outputFile.absolutePath,
            testDir.file("one.o").absolutePath].flatten()

        when:
        LinkerSpec spec = mockLinkerSpec(BundleLinkerSpec, outputFile, [testDir.file("one.o")])
        linker.execute(spec)

        then:
        1 * operationLogger.getLogLocation() >> LOG_LOCATION
        1 * buildOperationExecutor.runAll(commandLineTool, _) >> { worker, action -> action.execute(queue) }
        1 * invocationContext.getArgAction() >> Actions.doNothing()
        1 * invocationContext.createInvocation("linking lib", outputFile.parentFile, expectedArgs, operationLogger) >> invocation
        1 * queue.add(invocation)
        1 * queue.setLogLocation(LOG_LOCATION)
        0 * _
    }

    private LinkerSpec mockLinkerSpec(Class<? extends LinkerSpec> cls, File outputFile, List<File> objectFiles) {
        LinkerSpec spec = Mock(cls)
        spec.getSystemArgs() >> []
        spec.getArgs() >> []
        spec.getOutputFile() >> outputFile
        spec.getLibraries() >> []
        spec.getLibraryPath() >> []
        spec.getTargetPlatform() >> new DefaultNativePlatform("default")
        spec.getObjectFiles() >> objectFiles
        spec.getOperationLogger() >> operationLogger

        return spec
    }
}
