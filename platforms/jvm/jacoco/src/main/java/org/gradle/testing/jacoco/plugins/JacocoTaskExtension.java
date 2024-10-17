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
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.LocalState;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputFile;
import org.gradle.internal.instrumentation.api.annotations.NotToBeReplacedByLazyProperty;
import org.gradle.internal.instrumentation.api.annotations.ReplacesEagerProperty;
import org.gradle.internal.instrumentation.api.annotations.ToBeReplacedByLazyProperty;
import org.gradle.internal.jacoco.JacocoAgentJar;
import org.gradle.process.JavaForkOptions;
import org.gradle.util.internal.RelativePathUtil;

import javax.annotation.Nullable;
import javax.inject.Inject;
import java.io.File;
import java.util.Collection;
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
    private final ProviderFactory providers;

    private final RegularFileProperty destinationFile;

    /**
     * Creates a Jacoco task extension.
     *
     * @param agent the agent JAR to use for analysis
     * @param task the task we extend
     */
    @Inject
    public JacocoTaskExtension(ObjectFactory objects, ProviderFactory providers, JacocoAgentJar agent, JavaForkOptions task) {
        this.agent = agent;
        this.task = task;
        this.providers = providers;
        getEnabled().convention(true);
        this.destinationFile = objects.fileProperty();
        getIncludeNoLocationClasses().convention(false);
        getDumpOnExit().convention(true);
        getOutput().convention(Output.FILE);
        getPort().convention(0);
        getJmx().convention(false);
    }

    /**
     * Whether or not the task should generate execution data. Defaults to {@code true}.
     */
    @Input
    @ReplacesEagerProperty(originalType = boolean.class)
    public abstract Property<Boolean> getEnabled();

    @Internal
    @Deprecated
    @SuppressWarnings("InlineMeSuggester")
    public Property<Boolean> getIsEnabled() {
        // TODO: add deprecation message
        return getEnabled();
    }

    /**
     * The path for the execution data to be written to.
     */
    @Nullable
    @Optional
    @OutputFile
    @ToBeReplacedByLazyProperty(issue = "https://github.com/gradle/gradle/issues/29826")
    public File getDestinationFile() {
        return destinationFile.getAsFile().getOrNull();
    }

    /**
     * Set the provider for calculating the destination file.
     *
     * @param destinationFile Destination file provider
     * @since 4.0
     */
    public void setDestinationFile(Provider<File> destinationFile) {
        this.destinationFile.fileProvider(destinationFile);
    }

    public void setDestinationFile(File destinationFile) {
        this.destinationFile.set(destinationFile);
    }
    /**
     * List of class names that should be included in analysis. Names can use wildcards (* and ?). If left empty, all classes will be included. Defaults to an empty list.
     */
    @Optional
    @Input
    @ReplacesEagerProperty
    public abstract ListProperty<String> getIncludes();

    /**
     * List of class names that should be excluded from analysis. Names can use wildcard (* and ?). Defaults to an empty list.
     */
    @Optional
    @Input
    @ReplacesEagerProperty
    public abstract ListProperty<String> getExcludes();

    /**
     * List of classloader names that should be excluded from analysis. Names can use wildcards (* and ?). Defaults to an empty list.
     */
    @Optional
    @Input
    @ReplacesEagerProperty
    public abstract ListProperty<String> getExcludeClassLoaders();

    /**
     * Whether or not classes without source location should be instrumented. Defaults to {@code false}.
     *
     * This property is only taken into account if the used JaCoCo version supports this option (JaCoCo version &gt;= 0.7.6)
     */
    @Input
    @ReplacesEagerProperty(originalType = boolean.class)
    public abstract Property<Boolean> getIncludeNoLocationClasses();

    @Internal
    @Deprecated
    @SuppressWarnings("InlineMeSuggester")
    public Property<Boolean> getIsIncludeNoLocationClasses() {
        // TODO: add deprecation message
        return getIncludeNoLocationClasses();
    }

    /**
     * An identifier for the session written to the execution data. Defaults to an auto-generated identifier.
     */
    @Optional
    @Input
    @ReplacesEagerProperty
    public abstract Property<String> getSessionId();

    /**
     * Whether or not to dump the coverage data at VM shutdown. Defaults to {@code true}.
     */
    @Input
    @ReplacesEagerProperty(originalType = boolean.class)
    public abstract Property<Boolean> getDumpOnExit();

    @Internal
    @Deprecated
    @SuppressWarnings("InlineMeSuggester")
    public Property<Boolean> getIsDumpOnExit() {
        // TODO: add deprecation message
        return getDumpOnExit();
    }

    /**
     * The type of output to generate. Defaults to {@link Output#FILE}.
     */
    @Input
    @ReplacesEagerProperty
    public abstract Property<Output> getOutput();

    /**
     * IP address or hostname to use with {@link Output#TCP_SERVER} or {@link Output#TCP_CLIENT}. Defaults to localhost.
     */
    @Optional
    @Input
    @ReplacesEagerProperty
    public abstract Property<String> getAddress();

    /**
     * Port to bind to for {@link Output#TCP_SERVER} or {@link Output#TCP_CLIENT}. Defaults to 6300.
     */
    @Input
    @ReplacesEagerProperty(originalType = int.class)
    public abstract Property<Integer> getPort();

    /**
     * Path to dump all class files the agent sees are dumped to. Defaults to no dumps.
     *
     * @since 3.4
     */
    @Optional
    @LocalState
    @ReplacesEagerProperty
    public abstract DirectoryProperty getClassDumpDir();

    /**
     * Whether or not to expose functionality via JMX under {@code org.jacoco:type=Runtime}. Defaults to {@code false}.
     *
     * The configuration of the jmx property is only taken into account if the used JaCoCo version supports this option (JaCoCo version &gt;= 0.6.2)
     */
    @Input
    @ReplacesEagerProperty(originalType = boolean.class)
    public abstract Property<Boolean> getJmx();

    @Internal
    @Deprecated
    @SuppressWarnings("InlineMeSuggester")
    public Property<Boolean> getIsJmx() {
        // TODO: add deprecation message
        return getJmx();
    }

    /**
     * The Jacoco agent classpath.
     *
     * This contains only one file - the agent jar.
     *
     * @since 4.6
     */
    @Classpath
    @NotToBeReplacedByLazyProperty(because = "Read-only FileCollection property")
    public FileCollection getAgentClasspath() {
        return agent.getAgentConf();
    }

    /**
     * Gets all properties in the format expected of the agent JVM argument.
     *
     * @return state of extension in a JVM argument
     */
    @Internal
    @ReplacesEagerProperty
    public Provider<String> getAsJvmArg() {
        return providers.provider(() -> {
            StringBuilder builder = new StringBuilder();
            ArgumentAppender argument = new ArgumentAppender(builder, task.getWorkingDir());
            builder.append("-javaagent:");
            builder.append(agent.getJar().getAbsolutePath());
            builder.append('=');
            argument.append("destfile", getDestinationFile());
            argument.append("append", true);
            argument.append("includes", getIncludes().get());
            argument.append("excludes", getExcludes().get());
            argument.append("exclclassloader", getExcludeClassLoaders().get());
            if (agent.supportsInclNoLocationClasses()) {
                argument.append("inclnolocationclasses", getIncludeNoLocationClasses().getOrNull());
            }
            argument.append("sessionid", getSessionId().getOrNull());
            argument.append("dumponexit", getDumpOnExit().getOrNull());
            argument.append("output", getOutput().map(Output::getAsArg).getOrNull());
            argument.append("address", getAddress().getOrNull());
            argument.append("port", getPort().getOrNull());
            argument.append("classdumpdir", getClassDumpDir().getAsFile().getOrNull());

            if (agent.supportsJmx()) {
                argument.append("jmx", getJmx().getOrNull());
            }

            return builder.toString();
        });
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
                && !((value instanceof String) && StringUtils.isEmpty((String) value))
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
