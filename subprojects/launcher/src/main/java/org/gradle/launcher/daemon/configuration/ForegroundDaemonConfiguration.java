/*
 * Copyright 2009 the original author or authors.
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

package org.gradle.launcher.daemon.configuration;

import org.gradle.api.internal.file.IdentityFileResolver;
import org.gradle.process.internal.JvmOptions;

import java.io.File;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.List;

/**
 * by Szczepan Faber, created at: 2/21/12
 */
public class ForegroundDaemonConfiguration extends DefaultDaemonServerConfiguration {

    public ForegroundDaemonConfiguration(String daemonUid, File daemonBaseDir, int idleTimeoutMs) {
        super(daemonUid, daemonBaseDir, idleTimeoutMs, inferDaemonJvmOptions());
    }

    private static List<String> inferDaemonJvmOptions() {
        //foreground daemon cannot be 'told' what's his startup options as the client sits in the same process
        //so we will infer the jvm opts from the inputArguments()
        JvmOptions jvmOptions = new JvmOptions(new IdentityFileResolver());
        List<String> inputArguments = new ArrayList<String>(ManagementFactory.getRuntimeMXBean().getInputArguments());
        jvmOptions.setAllJvmArgs(inputArguments);
        //Simplification, we will make the foreground daemon interested only in managed jvm args
        return jvmOptions.getManagedJvmArgs();
    }
}
