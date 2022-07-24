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

package org.gradle.containers

import groovy.transform.CompileStatic
import groovy.transform.PackageScope
import org.gradle.integtests.fixtures.executer.ExecutionFailure
import org.gradle.integtests.fixtures.executer.ExecutionResult
import org.gradle.integtests.fixtures.executer.GradleDistribution
import org.gradle.integtests.fixtures.executer.GradleExecuter
import org.gradle.test.fixtures.file.TestDirectoryProvider
import org.spockframework.util.NotThreadSafe
import org.testcontainers.Testcontainers
import org.testcontainers.containers.BindMode
import org.testcontainers.containers.SelinuxContext
import org.testcontainers.utility.MountableFile

@CompileStatic
@NotThreadSafe
class GradleInContainer {
    static final String BASE_IMAGE = "openjdk:11-jre-slim"

    private final GradleContainer container
    private final GradleContainerExecuter executer

    private boolean started

    int containerMaxAliveSeconds = 120
    Thread monitor

    static void exposeHostPort(int port) {
        Testcontainers.exposeHostPorts(port)
    }

    GradleInContainer(GradleDistribution distribution, TestDirectoryProvider testDirectoryProvider, String baseImage = BASE_IMAGE) {
        container = new GradleContainer(baseImage)
        bindReadOnly(distribution.gradleHomeDir, "/gradle-under-test")
        executer = new GradleContainerExecuter(this, distribution, testDirectoryProvider)
        executer.withGradleUserHomeDir(new File("/gradle-home"))
    }

    GradleInContainer bindReadOnly(File local, String containerPath) {
        container.addFileSystemBind(local.absolutePath, containerPath, BindMode.READ_ONLY, SelinuxContext.NONE)
        this
    }

    GradleInContainer bindWritable(File local, String containerPath) {
        container.addFileSystemBind(local.absolutePath, containerPath, BindMode.READ_WRITE, SelinuxContext.NONE)
        this
    }

    GradleInContainer withExecuter(@DelegatesTo(value = GradleExecuter, strategy = Closure.DELEGATE_FIRST) Closure<?> action) {
        action.delegate = executer
        action.resolveStrategy = Closure.DELEGATE_FIRST
        action()
        this
    }

    GradleInContainer withBuildDir(File buildDirectory) {
        container.withWorkingDirectory("/test-build")
        container.withCopyFileToContainer(MountableFile.forHostPath(buildDirectory.toPath()), "/test-build")
        this
    }

    @PackageScope
    GradleExecResult execute(String... command) {
        if (!started) {
            container.withCommand("tail", "-f", "/dev/null")
            startContainer()
        }
        return container.execute(command)
    }

    void startContainer() {
        started = true
        container.start()
        monitor = new Thread(new Runnable() {
            @Override
            void run() {
                try {
                    Thread.sleep(containerMaxAliveSeconds * 1000)
                    System.err.println("Stopping container because of timeout of ${containerMaxAliveSeconds}s")
                    stopContainer()
                } catch (InterruptedException ex) {
                    // nothing to do
                }
            }
        })
        monitor.start()
    }

    void stopContainer() {
        if (started) {
            started = false
            container.stop()
            monitor.interrupt()
        }
    }

    GradleInContainer withEnv(Map<String, ?> stringMap) {
        Map<String, String> env = [:]
        stringMap.each { k, v -> env[k] = v?.toString() }
        container.withEnv(env)
        this
    }

    ExecutionResult succeeds(String... tasks) {
        executer.withTasks(tasks)
        executer.run()
    }

    ExecutionFailure fails(String... tasks) {
        executer.withTasks(tasks)
        executer.runWithFailure()
    }
}
