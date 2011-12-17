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

import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.collect.Lists;

import java.io.File;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

/**
 * Keep in mind that this is a serialized value object.
 *
 * @see DaemonContextBuilder
 */
public class DefaultDaemonContext implements DaemonContext {

    private final File javaHome;
    private final File daemonRegistryDir;
    private final Long pid;
    private final Integer idleTimeout;
    private final List<String> daemonOpts;

    public DefaultDaemonContext(File javaHome, File daemonRegistryDir, Long pid, Integer idleTimeout, List<String> daemonOpts) {
        this.javaHome = javaHome;
        this.daemonRegistryDir = daemonRegistryDir;
        this.pid = pid;
        this.idleTimeout = idleTimeout;
        this.daemonOpts = daemonOpts;
    }

    public static DaemonContext parseFrom(String source) {
        Pattern pattern = Pattern.compile("^.*DefaultDaemonContext\\[javaHome=([^\\n]+),daemonRegistryDir=([^\\n]+),pid=([^\\n]+),idleTimeout=(.+?),daemonOpts=([^\\n]+)].*",
                Pattern.MULTILINE + Pattern.DOTALL);
        Matcher matcher = pattern.matcher(source);

        if (matcher.matches()) {
            String javaHome = matcher.group(1);
            String daemonRegistryDir = matcher.group(2);
            Long pid = Long.parseLong(matcher.group(3));
            Integer idleTimeout = Integer.decode(matcher.group(4));
            List<String> jvmOpts = Lists.newArrayList(Splitter.on(',').split(matcher.group(5)));
            return new DefaultDaemonContext(new File(javaHome), new File(daemonRegistryDir), pid, idleTimeout, jvmOpts);
        } else {
            throw new IllegalStateException("unable to parse DefaultDaemonContext from source: " + source);
        }
    }

    public String toString() {
        return String.format("DefaultDaemonContext[javaHome=%s,daemonRegistryDir=%s,pid=%s,idleTimeout=%s,daemonOpts=%s]",
                javaHome, daemonRegistryDir, pid, idleTimeout, Joiner.on(',').join(daemonOpts));
    }

    public File getJavaHome() {
        return javaHome;
    }

    public File getDaemonRegistryDir() {
        return daemonRegistryDir;
    }

    public Long getPid() {
        return pid;
    }

    public Integer getIdleTimeout() {
        return idleTimeout;
    }

    public List<String> getDaemonOpts() {
        return daemonOpts;
    }
}