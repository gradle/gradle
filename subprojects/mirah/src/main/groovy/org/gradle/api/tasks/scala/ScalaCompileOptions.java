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

/**
 * Options for Scala compilation, including the use of the Ant-backed compiler.
 */
public class ScalaCompileOptions extends BaseScalaCompileOptions {
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

    private boolean fork;

    private boolean useAnt = true;

    private boolean useCompileDaemon;

    private String daemonServer;

    /**
     * Tells whether to use Ant for compilation. If {@code true}, the standard Ant scalac (or fsc) task will be used for
     * Scala and Java joint compilation. If {@code false}, the Zinc incremental compiler will be used
     * instead. The latter can be significantly faster, especially if there are few source code changes
     * between compiler runs. Defaults to {@code true}.
     */
    public boolean isUseAnt() {
        return useAnt;
    }

    public void setUseAnt(boolean useAnt) {
        this.useAnt = useAnt;
        if (!useAnt) {
            setFork(true);
        }
    }

    /**
     * Whether to run the Scala compiler in a separate process. Defaults to {@code false}
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

    // NOTE: Does not work for scalac 2.7.1 due to a bug in the Ant task
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
            return getLoggingPhases().isEmpty() ? " " : Joiner.on(',').join(getLoggingPhases());
        }
        if (fieldName.equals("additionalParameters")) {
            return getAdditionalParameters().isEmpty() ? " " : Joiner.on(' ').join(getAdditionalParameters());
        }
        return value;
    }

    private String toOnOffString(boolean flag) {
        return flag ? "on" : "off";
    }


}
