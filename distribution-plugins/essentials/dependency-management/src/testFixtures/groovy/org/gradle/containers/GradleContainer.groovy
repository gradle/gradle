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

import com.github.dockerjava.api.command.InspectContainerResponse
import com.github.dockerjava.api.exception.DockerException
import com.google.common.base.Charsets
import groovy.transform.CompileStatic
import org.testcontainers.DockerClientFactory
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.output.FrameConsumerResultCallback
import org.testcontainers.containers.output.OutputFrame
import org.testcontainers.containers.output.ToStringConsumer

@CompileStatic
class GradleContainer extends GenericContainer<GradleContainer> {
    GradleContainer(String image) {
        super(image)
    }

    @Override
    void close() {
        stop()
    }

    // this code is mostly copied from ExecInContainerPattern in order to be able to
    // follow Gradle execution live
    GradleExecResult execute(String... command) {
        if (!isRunning(containerInfo)) {
            throw new IllegalStateException("execInContainer can only be used while the Container is running");
        }
        def containerId = containerInfo.id
        def dockerClient = DockerClientFactory.instance().client()
        dockerClient.execCreateCmd(containerId).withCmd(command)
        def execCreateCmdResponse = dockerClient
            .execCreateCmd(containerId)
            .withAttachStdout(true)
            .withAttachStderr(true)
            .withCmd(command)
            .exec()
        def stdoutConsumer = new ToStringConsumer() {
            @Override
            void accept(OutputFrame outputFrame) {
                super.accept(outputFrame)
                println(outputFrame.utf8String)
            }
        }
        def stderrConsumer = new ToStringConsumer() {
            @Override
            void accept(OutputFrame outputFrame) {
                super.accept(outputFrame)
                System.err.println(outputFrame.utf8String)
            }
        }
        FrameConsumerResultCallback callback = new FrameConsumerResultCallback()
        callback.addConsumer(OutputFrame.OutputType.STDOUT, stdoutConsumer)
        callback.addConsumer(OutputFrame.OutputType.STDERR, stderrConsumer)
        dockerClient.execStartCmd(execCreateCmdResponse.getId()).exec(callback).awaitCompletion()
        Integer exitCode = dockerClient.inspectExecCmd(execCreateCmdResponse.getId()).exec().exitCode
        return new GradleExecResult(exitCode, stdoutConsumer.toString(Charsets.UTF_8), stderrConsumer.toString(Charsets.UTF_8))
    }

    private static boolean isRunning(InspectContainerResponse containerInfo) {
        try {
            return containerInfo != null && containerInfo.getState().getRunning()
        } catch (DockerException e) {
            return false
        }
    }
}
