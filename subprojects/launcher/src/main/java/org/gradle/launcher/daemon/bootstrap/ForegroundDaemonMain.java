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
package org.gradle.launcher.daemon.bootstrap;

import org.gradle.api.internal.file.IdentityFileResolver;
import org.gradle.launcher.daemon.configuration.DaemonServerConfiguration;
import org.gradle.launcher.daemon.registry.DaemonRegistry;
import org.gradle.launcher.daemon.server.Daemon;
import org.gradle.launcher.daemon.server.DaemonServices;
import org.gradle.logging.LoggingManagerInternal;
import org.gradle.logging.internal.OutputEventRenderer;
import org.gradle.process.internal.JvmOptions;

import java.io.File;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.List;

public class ForegroundDaemonMain extends DaemonMain {

    public ForegroundDaemonMain(DaemonServerConfiguration configuration) {
        super(configuration);

        List<String> opts = inferDaemonJvmOptions();
        addStartupJvmOptions(opts);
    }

    private List<String> inferDaemonJvmOptions() {
        //foregound daemon cannot be 'told' what's his startup options as the client sits in the same process
        //so we will infer the jvm opts from the inputArguments()
        JvmOptions jvmOptions = new JvmOptions(new IdentityFileResolver());
        List<String> inputArguments = new ArrayList<String>(ManagementFactory.getRuntimeMXBean().getInputArguments());
        jvmOptions.setAllJvmArgs(inputArguments);
        //Simplification, we will make the foreground daemon interested only in managed jvm args
        return jvmOptions.getMangedJvmArgs();
    }

    @Override
    protected void initialiseLogging(OutputEventRenderer renderer, LoggingManagerInternal loggingManager, File daemonLog) {
        // Don't redirect IO for foreground daemon
        loggingManager.start();
    }

    @Override
    protected Daemon startDaemon(DaemonServices daemonServices) {
        Daemon daemon = super.startDaemon(daemonServices);
        daemonServices.get(DaemonRegistry.class).markIdle(daemon.getAddress());
        return daemon;
    }
}
