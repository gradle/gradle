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
package org.gradle.api.tasks.mirah;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableMap;
import org.gradle.language.mirah.tasks.BaseMirahCompileOptions;

/**
 * Options for Mirah compilation.
 */
public class MirahCompileOptions extends BaseMirahCompileOptions {
    private boolean fork;

    private boolean useCompileDaemon;

    private String daemonServer;

    /**
     * Whether to run the Mirah compiler in a separate process. Defaults to {@code false}
     * for the Ant based compiler ({@code useAnt = true}), and to {@code true} for the Zinc
     * based compiler ({@code useAnt = false}).
     */
    public boolean isFork() {
        return fork;
    }

    public void setFork(boolean fork) {
        this.fork = fork;
    }

    /**
     * Whether to use the fsc compile daemon.
     */
    public boolean isUseCompileDaemon() {
        return useCompileDaemon;
    }

    public void setUseCompileDaemon(boolean useCompileDaemon) {
        this.useCompileDaemon = useCompileDaemon;
    }

    // NOTE: Does not work for mirahc 2.7.1 due to a bug in the Ant task
    /**
     * Server (host:port) on which the compile daemon is running.
     * The host must share disk access with the client process.
     * If not specified, launches the daemon on the localhost.
     * This parameter can only be specified if useCompileDaemon is true.
     */
    public String getDaemonServer() {
        return daemonServer;
    }

    public void setDaemonServer(String daemonServer) {
        this.daemonServer = daemonServer;
    }

}
