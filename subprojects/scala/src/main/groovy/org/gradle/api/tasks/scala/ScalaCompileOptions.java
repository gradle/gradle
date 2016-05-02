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
package org.gradle.api.tasks.scala;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableMap;
import org.gradle.language.scala.tasks.BaseScalaCompileOptions;
import org.gradle.util.CollectionUtils;
import org.gradle.util.SingleMessageLogger;

/**
 * Options for Scala compilation, including the use of the Ant-backed compiler.
 */
public class ScalaCompileOptions extends BaseScalaCompileOptions {
    private static final String USE_ANT_MSG = "The Ant-Based Scala compiler is deprecated, please see "
        + "https://docs.gradle.org/current/userguide/scala_plugin.html";
    private static final String FORK_MSG = "The fork option for the Scala compiler is deprecated, please see "
        + "https://docs.gradle.org/current/userguide/scala_plugin.html";
    private static final String USE_COMPILE_DAEMON_MSG = "The Ant-Based Scala compiler and it's support for a compile "
        + "daemon is deprecated, please see https://docs.gradle.org/current/userguide/scala_plugin.html";

    private static final ImmutableMap<String, String> FIELD_NAMES_TO_ANT_PROPERTIES = new ImmutableMap.Builder<String, String>()
            .put("loggingLevel", "logging")
            .put("loggingPhases", "logphase")
            .put("targetCompatibility", "target")
            .put("optimize", "optimise")
            .put("daemonServer", "server")
            .put("listFiles", "scalacdebugging")
            .put("debugLevel", "debuginfo")
            .put("additionalParameters", "addparams")
            .build();

    protected boolean fork = true;

    protected boolean useAnt;

    protected boolean useCompileDaemon;

    protected String daemonServer;

    /**
     * Tells whether to use Ant for compilation. If {@code true}, the standard Ant scalac (or fsc) task will be used for
     * Scala and Java joint compilation. If {@code false}, the Zinc incremental compiler will be used
     * instead. The latter can be significantly faster, especially if there are few source code changes
     * between compiler runs. Defaults to {@code true}.
     */
    @Deprecated
    public boolean isUseAnt() {
        SingleMessageLogger.nagUserOfDeprecated("useAnt", USE_ANT_MSG);
        return useAnt;
    }


    @Deprecated
    public void setUseAnt(boolean useAnt) {
        SingleMessageLogger.nagUserOfDeprecated("useAnt", USE_ANT_MSG);
        this.useAnt = useAnt;
        if (useAnt == fork) {
            // Not using setFork so we don't get two deprecation nag messages.
            fork = !useAnt;
        }
    }

    /**
     * Whether to run the Scala compiler in a separate process. Defaults to {@code false}
     * for the Ant based compiler ({@code useAnt = true}), and to {@code true} for the Zinc
     * based compiler ({@code useAnt = false}).
     */
    @Deprecated
    public boolean isFork() {
        SingleMessageLogger.nagUserOfDeprecated("fork", FORK_MSG);
        return fork;
    }

    @Deprecated
    public void setFork(boolean fork) {
        SingleMessageLogger.nagUserOfDeprecated("fork", FORK_MSG);
        this.fork = fork;
    }

    /**
     * Whether to use the fsc compile daemon.
     */
    @Deprecated
    public boolean isUseCompileDaemon() {
        SingleMessageLogger.nagUserOfDeprecated("useCompileDaemon", USE_COMPILE_DAEMON_MSG);
        return useCompileDaemon;
    }

    @Deprecated
    public void setUseCompileDaemon(boolean useCompileDaemon) {
        SingleMessageLogger.nagUserOfDeprecated("useCompileDaemon", USE_COMPILE_DAEMON_MSG);
        this.useCompileDaemon = useCompileDaemon;
    }

    // NOTE: Does not work for scalac 2.7.1 due to a bug in the Ant task
    /**
     * Server (host:port) on which the compile daemon is running.
     * The host must share disk access with the client process.
     * If not specified, launches the daemon on the localhost.
     * This parameter can only be specified if useCompileDaemon is true.
     */
    @Deprecated
    public String getDaemonServer() {
        SingleMessageLogger.nagUserOfDeprecated("daemonServer", USE_COMPILE_DAEMON_MSG);
        return daemonServer;
    }

    @Deprecated
    public void setDaemonServer(String daemonServer) {
        SingleMessageLogger.nagUserOfDeprecated("daemonServer", USE_COMPILE_DAEMON_MSG);
        this.daemonServer = daemonServer;
    }

    protected boolean excludeFromAntProperties(String fieldName) {
        return fieldName.equals("useCompileDaemon")
                || fieldName.equals("forkOptions")
                || fieldName.equals("useAnt")
                || fieldName.equals("incrementalOptions")
                || fieldName.equals("targetCompatibility") // handled directly by AntScalaCompiler
                || fieldName.equals("optimize") && !isOptimize();
    }

    protected String getAntPropertyName(String fieldName) {
        if (FIELD_NAMES_TO_ANT_PROPERTIES.containsKey(fieldName)) {
            return FIELD_NAMES_TO_ANT_PROPERTIES.get(fieldName);
        }
        return fieldName;
    }

    protected Object getAntPropertyValue(String fieldName, Object value) {
        if (fieldName.equals("deprecation")) {
            return toOnOffString(isDeprecation());
        }
        if (fieldName.equals("unchecked")) {
            return toOnOffString(isUnchecked());
        }
        if (fieldName.equals("optimize")) {
            return toOnOffString(isOptimize());
        }
        if (fieldName.equals("loggingPhases")) {
            return Joiner.on(",").join(getLoggingPhases());
        }
        if (fieldName.equals("additionalParameters")) {
            return CollectionUtils.asCommandLine(getAdditionalParameters());
        }
        return value;
    }

    private String toOnOffString(boolean flag) {
        return flag ? "on" : "off";
    }


}
