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
import org.gradle.api.testing.execution.PipelineDispatcher;
import org.gradle.api.testing.execution.QueueingPipeline;
import org.gradle.api.testing.execution.control.client.ForkLaunchMain;
import org.gradle.api.testing.execution.control.client.TestForkExecuter;
import org.gradle.api.testing.execution.control.server.ControlServerFactory;
import org.gradle.api.testing.execution.control.server.TestControlServer;
import org.gradle.api.testing.execution.fork.ForkConfigWriter;
import org.gradle.api.testing.execution.fork.ForkControl;
import org.gradle.api.testing.execution.fork.ForkControlListener;
import org.gradle.api.testing.execution.fork.ForkInfo;
import org.gradle.api.testing.execution.fork.policies.ForkPolicyForkInfo;
import org.gradle.api.testing.fabric.TestFrameworkInstance;
import org.gradle.util.Jvm;
import org.gradle.util.exec.ExecHandle;
import org.gradle.util.exec.ExecHandleBuilder;
import org.gradle.util.exec.ExecHandleListener;

import java.io.ByteArrayInputStream;

/**
 * @author Tom Eyckmans
 */
public class LocalSimpleForkPolicyForkInfo implements ForkPolicyForkInfo {
    private final ForkInfo forkInfo;
    private final ControlServerFactory serverFactory;
    private final ForkControl forkControl;
    private final PipelineDispatcher pipelineDispatcher;

    public LocalSimpleForkPolicyForkInfo(ForkInfo forkInfo, ControlServerFactory serverFactory, ForkControl forkControl,
                                         PipelineDispatcher pipelineDispatcher) {
        this.forkInfo = forkInfo;
        this.serverFactory = serverFactory;
        this.forkControl = forkControl;
        this.pipelineDispatcher = pipelineDispatcher;
    }

    public void start() {
        final TestControlServer server = serverFactory.createTestControlServer(pipelineDispatcher);

        QueueingPipeline pipeline = forkInfo.getPipeline();
        NativeTest testTask = pipeline.getTestTask();
        int pipelineId = pipeline.getId();
        Project project = testTask.getProject();
        TestFrameworkInstance testFramework = testTask.getTestFramework();
        ForkConfigWriter forkConfigWriter = new ForkConfigWriter(testTask, pipelineId, forkInfo.getId(),
                server.getLocalAddress());
        String forkConfigFile = forkConfigWriter.writeConfigFile();

        // TODO we probably want loggers for each fork
        ExecHandleBuilder forkHandleBuilder = new ExecHandleBuilder().execDirectory(project.getProjectDir())
                .execCommand(Jvm.current().getJavaExecutable());

        testFramework.applyForkJvmArguments(forkHandleBuilder);

        forkHandleBuilder.arguments("-cp", System.getProperty("gradle.fork.launcher.cp"),
                ForkLaunchMain.class.getName(), TestForkExecuter.class.getName());
        forkHandleBuilder.standardInput(new ByteArrayInputStream(forkConfigFile.getBytes()));

        testFramework.applyForkArguments(forkHandleBuilder);

        ExecHandle forkHandle = forkHandleBuilder.getExecHandle();

        forkHandle.addListeners(new ForkControlListener(forkControl, forkInfo.getPipeline().getId(), forkInfo.getId()));
        forkHandle.addListeners(new ExecHandleListener() {
            public void executionStarted(ExecHandle execHandle) {
            }

            public void executionFinished(ExecHandle execHandle) {
                server.stop();
            }
        });

        forkHandle.start();
    }
}
