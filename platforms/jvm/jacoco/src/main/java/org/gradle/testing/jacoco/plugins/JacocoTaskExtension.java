/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.testing.jacoco.plugins;

import com.google.common.base.Joiner;
import org.apache.commons.lang.StringUtils;
import org.gradle.api.file.FileCollection;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.LocalState;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputFile;
import org.gradle.internal.jacoco.JacocoAgentJar;
import org.gradle.process.JavaForkOptions;
import org.gradle.util.internal.RelativePathUtil;

import javax.annotation.Nullable;
import javax.inject.Inject;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

/**
 * Extension for tasks that should run with a Jacoco agent to generate coverage execution data.
 */
public abstract class JacocoTaskExtension {

    /**
     * The types of output that the agent can use for execution data.
     */
    public enum Output {
        FILE,
        TCP_SERVER,
        TCP_CLIENT,
        NONE;

        /**
         * Gets type in format of agent argument.
         */
        public String getAsArg() {
            return toString().toLowerCase(Locale.US).replaceAll("_", "");
        }
    }

    private final JacocoAgentJar agent;
    private final JavaForkOptions task;

    private boolean enabled = true;
    private final Property<File> destinationFile;
    private List<String> includes = new ArrayList<>();
    private List<String> excludes = new ArrayList<>();
    private List<String> excludeClassLoaders = new ArrayList<>();
    private boolean includeNoLocationClasses;
    private String sessionId;
    private boolean dumpOnExit = true;
    private Output output = Output.FILE;
    private String address;
    private int port;
    private File classDumpDir;
    private boolean jmx;

    /**
     * Creates a Jacoco task extension.
     *
     * @param objects the object factory
     * @param agent the agent JAR to use for analysis
     * @param task the task we extend
     */
    @Inject
    public JacocoTaskExtension(ObjectFactory objects, JacocoAgentJar agent, JavaForkOptions task) {
        this.agent = agent;
        this.task = task;
        destinationFile = objects.property(File.class);
    }

    /**
     * Whether or not the task should generate execution data. Defaults to {@code true}.
     */
    @Input
    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * The path for the execution data to be written to.
     */
    @Nullable
    @Optional
    @OutputFile
    public File getDestinationFile() {
        return destinationFile.getOrNull();
    }

    /**
     * Set the provider for calculating the destination file.
     *
     * @param destinationFile Destination file provider
     * @since 4.0
     */
    public void setDestinationFile(Provider<File> destinationFile) {
        this.destinationFile.set(destinationFile);
    }

    public void setDestinationFile(File destinationFile) {
        this.destinationFile.set(destinationFile);
    }

    /**
     * List of class names that should be included in analysis. Names can use wildcards (* and ?). If left empty, all classes will be included. Defaults to an empty list.
     */
    @Nullable
    @Optional
    @Input
    public List<String> getIncludes() {
        return includes;
    }

    public void setIncludes(@Nullable List<String> includes) {
        this.includes = includes;
    }

    /**
     * List of class names that should be excluded from analysis. Names can use wildcard (* and ?). Defaults to an empty list.
     */
    @Nullable
    @Optional
    @Input
    public List<String> getExcludes() {
        return excludes;
    }

    public void setExcludes(@Nullable List<String> excludes) {
        this.excludes = excludes;
    }

    /**
     * List of classloader names that should be excluded from analysis. Names can use wildcards (* and ?). Defaults to an empty list.
     */
    @Nullable
    @Optional
    @Input
    public List<String> getExcludeClassLoaders() {
        return excludeClassLoaders;
    }

    public void setExcludeClassLoaders(@Nullable List<String> excludeClassLoaders) {
        this.excludeClassLoaders = excludeClassLoaders;
    }

    /**
     * Whether or not classes without source location should be instrumented. Defaults to {@code false}.
     *
     * This property is only taken into account if the used JaCoCo version supports this option (JaCoCo version &gt;= 0.7.6)
     */
    @Input
    public boolean isIncludeNoLocationClasses() {
        return includeNoLocationClasses;
    }

    public void setIncludeNoLocationClasses(boolean includeNoLocationClasses) {
        this.includeNoLocationClasses = includeNoLocationClasses;
    }

    /**
     * An identifier for the session written to the execution data. Defaults to an auto-generated identifier.
     */
    @Nullable
    @Optional
    @Input
    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(@Nullable String sessionId) {
        this.sessionId = sessionId;
    }

    /**
     * Whether or not to dump the coverage data at VM shutdown. Defaults to {@code true}.
     */
    @Input
    public boolean isDumpOnExit() {
        return dumpOnExit;
    }

    public void setDumpOnExit(boolean dumpOnExit) {
        this.dumpOnExit = dumpOnExit;
    }

    /**
     * The type of output to generate. Defaults to {@link Output#FILE}.
     */
    @Input
    public Output getOutput() {
        return output;
    }

    public void setOutput(Output output) {
        this.output = output;
    }

    /**
     * IP address or hostname to use with {@link Output#TCP_SERVER} or {@link Output#TCP_CLIENT}. Defaults to localhost.
     */
    @Nullable
    @Optional
    @Input
    public String getAddress() {
        return address;
    }

    public void setAddress(@Nullable String address) {
        this.address = address;
    }

    /**
     * Port to bind to for {@link Output#TCP_SERVER} or {@link Output#TCP_CLIENT}. Defaults to 6300.
     */
    @Input
    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    /**
     * Path to dump all class files the agent sees are dumped to. Defaults to no dumps.
     *
     * @since 3.4
     */
    @Nullable
    @Optional
    @LocalState
    public File getClassDumpDir() {
        return classDumpDir;
    }

    /**
     * Sets path to dump all class files the agent sees are dumped to. Defaults to no dumps.
     *
     * @since 3.4
     */
    public void setClassDumpDir(@Nullable File classDumpDir) {
        this.classDumpDir = classDumpDir;
    }

    /**
     * Whether or not to expose functionality via JMX under {@code org.jacoco:type=Runtime}. Defaults to {@code false}.
     *
     * The configuration of the jmx property is only taken into account if the used JaCoCo version supports this option (JaCoCo version &gt;= 0.6.2)
     */
    @Input
    public boolean isJmx() {
        return jmx;
    }

    public void setJmx(boolean jmx) {
        this.jmx = jmx;
    }

    /**
     * The Jacoco agent classpath.
     *
     * This contains only one file - the agent jar.
     *
     * @since 4.6
     */
    @Classpath
    public FileCollection getAgentClasspath() {
        return agent.getAgentConf();
    }

    /**
     * Gets all properties in the format expected of the agent JVM argument.
     *
     * @return state of extension in a JVM argument
     */
    @Internal
    public String getAsJvmArg() {
        StringBuilder builder = new StringBuilder();
        ArgumentAppender argument = new ArgumentAppender(builder, task.getWorkingDir());
        builder.append("-javaagent:");
        builder.append(agent.getJar().getAbsolutePath());
        builder.append('=');
        argument.append("destfile", getDestinationFile());
        argument.append("append", true);
        argument.append("includes", getIncludes());
        argument.append("excludes", getExcludes());
        argument.append("exclclassloader", getExcludeClassLoaders());
        if (agent.supportsInclNoLocationClasses()) {
            argument.append("inclnolocationclasses", isIncludeNoLocationClasses());
        }
        argument.append("sessionid", getSessionId());
        argument.append("dumponexit", isDumpOnExit());
        argument.append("output", getOutput().getAsArg());
        argument.append("address", getAddress());
        argument.append("port", getPort());
        argument.append("classdumpdir", getClassDumpDir());

        if (agent.supportsJmx()) {
            argument.append("jmx", isJmx());
        }

        return builder.toString();
    }

    private static class ArgumentAppender {

        private final StringBuilder builder;
        private final File workingDirectory;
        private boolean anyArgs;

        public ArgumentAppender(StringBuilder builder, File workingDirectory) {
            this.builder = builder;
            this.workingDirectory = workingDirectory;
        }

        public void append(String name, @Nullable Object value) {
            if (value != null
                && !((value instanceof Collection) && ((Collection) value).isEmpty())
                && !((value instanceof String) && (StringUtils.isEmpty((String) value)))
                && !((value instanceof Integer) && ((Integer) value == 0))) {
                if (anyArgs) {
                    builder.append(',');
                }
                builder.append(name).append('=');
                if (value instanceof Collection) {
                    builder.append(Joiner.on(':').join((Collection<?>) value));
                } else if (value instanceof File) {
                    builder.append(RelativePathUtil.relativePath(workingDirectory, (File) value));
                } else {
                    builder.append(value);
                }
                anyArgs = true;
            }
        }
    }
}
