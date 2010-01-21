/*
 * Copyright 2010 the original author or authors.
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
import org.gradle.api.testing.execution.QueueingPipeline;
import org.gradle.api.testing.execution.control.server.TestControlServer;
import org.gradle.api.testing.execution.fork.ForkConfigWriter;
import org.gradle.api.testing.execution.fork.ForkControl;
import org.gradle.api.testing.execution.fork.ForkControlListener;
import org.gradle.api.testing.execution.fork.ForkInfo;
import org.gradle.api.testing.execution.fork.policies.ForkPolicyForkInfo;
import org.gradle.api.testing.fabric.TestFrameworkInstance;
import org.gradle.util.exec.DummyExecOutputHandle;
import org.gradle.util.exec.ExecHandle;
import org.gradle.util.exec.ExecHandleBuilder;

import java.io.File;

/**
 * @author Tom Eyckmans
 */
public class LocalSimpleForkPolicyForkInfo implements ForkPolicyForkInfo {
    private final ForkInfo forkInfo;
    private final TestControlServer server;
    private final ForkControl forkControl;

    public LocalSimpleForkPolicyForkInfo(ForkInfo forkInfo, TestControlServer server, ForkControl forkControl) {
        this.forkInfo = forkInfo;
        this.server = server;
        this.forkControl = forkControl;
    }

    public void start() {
        QueueingPipeline pipeline = forkInfo.getPipeline();
        NativeTest testTask = pipeline.getTestTask();
        int pipelineId = pipeline.getId();
        Project project = testTask.getProject();
        TestFrameworkInstance testFramework = testTask.getTestFramework();
        ForkConfigWriter forkConfigWriter = new ForkConfigWriter(testTask, pipelineId, forkInfo.getId(),
                server.getPort());
        File forkConfigFile = forkConfigWriter.writeConfigFile();

        ExecHandleBuilder forkHandleBuilder = new ExecHandleBuilder(
                false) // TODO we probably want loggers for each fork
                .execDirectory(project.getRootDir()).execCommand("java").errorOutputHandle(new DummyExecOutputHandle())
                .standardOutputHandle(new DummyExecOutputHandle());

        testFramework.applyForkJvmArguments(forkHandleBuilder);

        forkHandleBuilder.arguments("-cp", System.getProperty("gradle.fork.launcher.cp"),
                "org.gradle.api.testing.execution.fork.ForkLaunchMain", forkConfigFile.getAbsolutePath(),
                "org.gradle.api.testing.execution.control.client.TestForkExecuter");

        testFramework.applyForkArguments(forkHandleBuilder);

        ExecHandle forkHandle = forkHandleBuilder.getExecHandle();

        forkHandle.addListeners(new ForkControlListener(forkControl, forkInfo.getPipeline().getId(), forkInfo.getId()));
        forkHandle.start();
    }
}
