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

package org.gradle.api.testing.execution.control.client;

import org.gradle.api.tasks.testing.NativeTest;
import org.gradle.util.BootstrapUtil;
import org.gradle.util.GUtil;

import java.io.File;
import java.net.URI;

/**
 * @author Tom Eyckmans
 */
public class ForkConfigWriter {

    private final NativeTest testTask;
    private final int pipelineId;
    private final int forkId;
    private final URI testServerAddress;

    public ForkConfigWriter(NativeTest testTask, int pipelineId, int forkId, URI testServerAddress) {
        this.testTask = testTask;
        this.pipelineId = pipelineId;
        this.forkId = forkId;
        this.testServerAddress = testServerAddress;
    }

    public byte[] writeConfigFile() {
        ForkProcessConfig config = new ForkProcessConfig();

        // TODO test framework spec from testRuntime
        for (File sharedCpElement : testTask.getClasspath()) {
            // TODO use spec
            if (sharedCpElement.getName().startsWith("junit-") || sharedCpElement.getName().startsWith("testng-")) {
                config.shared(sharedCpElement);
            }
        }

        // TODO only needed Gradle fork classpath
        for (File controlCpElement : BootstrapUtil.getGradleClasspath()) {
            config.control(controlCpElement);
        }

        // TODO testRuntime classpath without test framework spec
        for (File sandboxCpElement : testTask.getClasspath()) {
            if (!sandboxCpElement.getName().startsWith("junit-") && !sandboxCpElement.getName().startsWith("testng-")) {
                config.sandbox(sandboxCpElement);
            }
        }

        config.setPipelineId(pipelineId);
        config.setForkId(forkId);
        config.setServerAddress(testServerAddress);
        config.setTestFrameworkId(testTask.getTestFramework().getTestFramework().getId());

        return GUtil.serialize(config);
    }
}
