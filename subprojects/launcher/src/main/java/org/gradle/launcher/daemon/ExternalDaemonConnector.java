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
package org.gradle.launcher.daemon;

import org.gradle.api.internal.DefaultClassPathRegistry;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.initialization.DefaultCommandLineConverter;
import org.gradle.util.GUtil;
import org.gradle.util.Jvm;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * A daemon connector that starts daemons by launching new processes.
 */
public class ExternalDaemonConnector extends AbstractDaemonConnector<PersistentDaemonRegistry> {

    private static final Logger LOGGER = Logging.getLogger(ExternalDaemonConnector.class);
    public static final int DEFAULT_IDLE_TIMEOUT = 3 * 60 * 60 * 1000;
    
    private final File userHomeDir;
    private final int idleTimeout;
    
    public ExternalDaemonConnector(File userHomeDir) {
        this(userHomeDir, DEFAULT_IDLE_TIMEOUT);
    }

    ExternalDaemonConnector(File userHomeDir, int idleTimeout) {
        super(new PersistentDaemonRegistry(userHomeDir));
        this.idleTimeout = idleTimeout;
        this.userHomeDir = userHomeDir;
    }

    protected void startDaemon() {
        Set<File> bootstrapClasspath = new DefaultClassPathRegistry().getClassPathFiles("GRADLE_BOOTSTRAP");
        if (bootstrapClasspath.isEmpty()) {
            throw new IllegalStateException("Unable to construct a bootstrap classpath when starting the daemon");
        }
        
        List<String> daemonArgs = new ArrayList<String>();
        daemonArgs.add(Jvm.current().getJavaExecutable().getAbsolutePath());
        daemonArgs.add("-Xmx1024m");
        daemonArgs.add("-XX:MaxPermSize=256m");
        //TODO SF - remove later
//        daemonArgs.add("-Xdebug");
//        daemonArgs.add("-Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=5006");
        daemonArgs.add("-cp");
        daemonArgs.add(GUtil.join(bootstrapClasspath, File.pathSeparator));
        daemonArgs.add(GradleDaemon.class.getName());
        daemonArgs.add(String.format("-%s", DefaultCommandLineConverter.GRADLE_USER_HOME));
        daemonArgs.add(userHomeDir.getAbsolutePath());

        //TODO SF daemon server should use all gradle opts (that requires more digging & windows validation) but it is a part of a different story
        //for now I only pass the idle timeout
        DaemonTimeout timeout = new DaemonTimeout(System.getenv("GRADLE_OPTS"), idleTimeout);
        daemonArgs.add(timeout.toSysArg());

        DaemonStartAction daemon = new DaemonStartAction();
        daemon.args(daemonArgs);
        daemon.workingDir(userHomeDir);
        daemon.start();
    }

}
