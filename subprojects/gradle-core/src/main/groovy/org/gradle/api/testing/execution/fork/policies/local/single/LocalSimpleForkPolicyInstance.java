/*
 * Copyright 2007-2009 the original author or authors.
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
package org.gradle.api.testing.execution.fork.policies.local.single;

import org.gradle.api.Project;
import org.gradle.api.tasks.testing.NativeTest;
import org.gradle.api.testing.execution.Pipeline;
import org.gradle.api.testing.execution.control.server.TestServersManager;
import org.gradle.api.testing.execution.fork.ForkConfigWriter;
import org.gradle.api.testing.execution.fork.ForkControl;
import org.gradle.api.testing.execution.fork.ForkControlListener;
import org.gradle.api.testing.execution.fork.ForkInfo;
import org.gradle.api.testing.execution.fork.policies.ForkPolicyForkInfo;
import org.gradle.api.testing.execution.fork.policies.ForkPolicyInstance;
import org.gradle.api.testing.fabric.TestFrameworkInstance;
import org.gradle.util.exec.ExecHandle;
import org.gradle.util.exec.ExecHandleBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

/**
 * @author Tom Eyckmans
 */
public class LocalSimpleForkPolicyInstance implements ForkPolicyInstance {
    private static final Logger logger = LoggerFactory.getLogger(LocalSimpleForkPolicyInstance.class);

    private final Pipeline pipeline;
    private final ForkControl forkControl;
    private final TestServersManager testServersManager;

    private int controlServerPort;

    public LocalSimpleForkPolicyInstance(Pipeline pipeline, ForkControl forkControl, TestServersManager testServersManager) {
        if (pipeline == null) throw new IllegalArgumentException("pipeline is null!");
        if (forkControl == null) throw new IllegalArgumentException("forkControl is null!");
        if (testServersManager == null) throw new IllegalArgumentException("testServersManager is null!");

        this.pipeline = pipeline;
        this.forkControl = forkControl;
        this.testServersManager = testServersManager;
    }

    public ForkPolicyForkInfo createForkPolicyForkInfo() {
        return new LocalSimpleForkPolicyForkInfo(this);
    }

    public void initialize() {

        final int pipelineId = pipeline.getId();

        logger.warn("Setting up test server & fork for pipeline {}", pipelineId);

        // startup the test server
        controlServerPort = testServersManager.addAndStartServer(pipeline);

    }

    public void startFork(ForkInfo forkInfo) {
        final LocalSimpleForkPolicyForkInfo policyInfo = (LocalSimpleForkPolicyForkInfo) forkInfo.getForkPolicyInfo();

        final ExecHandleBuilder forkHandleBuilder = policyInfo.getForkHandleBuilder();

        final ExecHandle forkHandle = forkHandleBuilder.getExecHandle();

        policyInfo.setForkHandle(forkHandle);

        forkHandle.addListeners(new ForkControlListener(forkControl, forkInfo.getPipeline().getId(), forkInfo.getId()));
        forkHandle.start();
    }

    public void stop() {
        testServersManager.stopServer(pipeline);
    }

    public void initializeFork(ForkInfo forkInfo) {
        final LocalSimpleForkPolicyForkInfo policyInfo = (LocalSimpleForkPolicyForkInfo) forkInfo.getForkPolicyInfo();
        final Pipeline pipeline = forkInfo.getPipeline();
        final NativeTest testTask = pipeline.getTestTask();
        final int pipelineId = pipeline.getId();
        final Project project = testTask.getProject();
        final TestFrameworkInstance testFramework = testTask.getTestFramework();
        final int forkId = forkControl.getNextForkId();
        final ForkConfigWriter forkConfigWriter = new ForkConfigWriter(testTask, pipelineId, forkId, controlServerPort);
        final File forkConfigFile = forkConfigWriter.writeConfigFile();

        final ExecHandleBuilder forkHandleBuilder = new ExecHandleBuilder(false) // TODO we probably want loggers for each fork
                .execDirectory(project.getRootDir())
                .execCommand("java");

        testFramework.applyForkJvmArguments(forkHandleBuilder);

        forkHandleBuilder.arguments(
                "-cp",
                System.getProperty("gradle.fork.launcher.cp"),
                "org.gradle.api.testing.execution.fork.ForkLaunchMain",
                forkConfigFile.getAbsolutePath(),
                "org.gradle.api.testing.execution.control.client.TestForkExecuter"
        );

        testFramework.applyForkArguments(forkHandleBuilder);

        policyInfo.setForkHandleBuilder(forkHandleBuilder);
    }
}
