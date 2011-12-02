/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.launcher.daemon.context;

import com.google.common.collect.Lists;
import org.gradle.StartParameter;
import org.gradle.util.Jvm;
import org.gradle.os.jna.NativeEnvironment;
import static org.gradle.util.GFileUtils.canonicalise;
import org.gradle.api.internal.Factory;
import org.gradle.launcher.daemon.registry.DaemonDir;

import java.io.File;
import java.util.List;

/**
 * Builds a daemon context, reflecting the current environment.
 * <p>
 * The builder itself has properties for different context values, that allow you to override
 * what would be set based on the environment. This is primarily to aid in testing.
 */
public class DaemonContextBuilder implements Factory<DaemonContext> {

    private File javaHome;
    private File daemonRegistryDir;
    private Long pid;
    private Integer idleTimeout;
    private List<String> daemonOpts = Lists.newArrayList();

    public DaemonContextBuilder() {
        javaHome = canonicalise(Jvm.current().getJavaHome());
        daemonRegistryDir = DaemonDir.getDirectoryInGradleUserHome(StartParameter.DEFAULT_GRADLE_USER_HOME);
        pid = NativeEnvironment.current().maybeGetPid();
    }

    public File getJavaHome() {
        return javaHome;
    }

    public void setJavaHome(File javaHome) {
        this.javaHome = javaHome;
    }

    public File getDaemonRegistryDir() {
        return this.daemonRegistryDir = daemonRegistryDir;
    }

    public void setDaemonRegistryDir(File daemonRegistryDir) {
        this.daemonRegistryDir = daemonRegistryDir;
    }

    public Long getPid() {
        return pid;
    }
    
    public void setPid(Long pid) {
        this.pid = pid;
    }

    public Integer getIdleTimeout() {
        return idleTimeout;
    }
    
    public void setIdleTimeout(Integer idleTimeout) {
        this.idleTimeout = idleTimeout;
    }

    public List<String> getDaemonOpts() {
        return daemonOpts;
    }

    public void setDaemonOpts(List<String> daemonOpts) {
        this.daemonOpts = daemonOpts;
    }

    /**
     * Creates a new daemon context, based on the current state of this builder.
     */
    public DaemonContext create() {
        return new DefaultDaemonContext(javaHome, daemonRegistryDir, pid, idleTimeout, daemonOpts);
    }
}