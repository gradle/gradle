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

package org.gradle.process.internal;

import com.google.common.collect.ImmutableSet;
import org.apache.commons.lang.StringUtils;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.file.FileCollectionFactory;
import org.gradle.process.JavaDebugOptions;
import org.gradle.process.JavaForkOptions;
import org.gradle.util.internal.GUtil;
import org.gradle.util.internal.ArgumentsSplitter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

public class JvmOptions {
    private static final String XMS_PREFIX = "-Xms";
    private static final String XMX_PREFIX = "-Xmx";
    private static final String BOOTCLASSPATH_PREFIX = "-Xbootclasspath:";

    public static final String FILE_ENCODING_KEY = "file.encoding";
    public static final String USER_LANGUAGE_KEY = "user.language";
    public static final String USER_COUNTRY_KEY = "user.country";
    public static final String USER_VARIANT_KEY = "user.variant";
    public static final String JMX_REMOTE_KEY = "com.sun.management.jmxremote";
    public static final String JAVA_IO_TMPDIR_KEY = "java.io.tmpdir";

    private static final Logger LOGGER = LoggerFactory.getLogger(JvmOptions.class);

    public static final Set<String> IMMUTABLE_SYSTEM_PROPERTIES = ImmutableSet.of(
        FILE_ENCODING_KEY, USER_LANGUAGE_KEY, USER_COUNTRY_KEY, USER_VARIANT_KEY, JMX_REMOTE_KEY, JAVA_IO_TMPDIR_KEY
    );

    // Store this because Locale.default is mutable and we want the unchanged default
    // We are assuming this class will be initialized before any code has a chance to change the default
    private static final Locale DEFAULT_LOCALE = Locale.getDefault();

    private final List<Object> extraJvmArgs = new ArrayList<Object>();
    private final Map<String, Object> mutableSystemProperties = new TreeMap<String, Object>();
    private final FileCollectionFactory fileCollectionFactory;

    private ConfigurableFileCollection bootstrapClasspath;
    private String minHeapSize;
    private String maxHeapSize;
    private boolean assertionsEnabled;

    private final JavaDebugOptions debugOptions;

    protected final Map<String, Object> immutableSystemProperties = new TreeMap<String, Object>();

    public JvmOptions(FileCollectionFactory fileCollectionFactory, JavaDebugOptions debugOptions) {
        this.debugOptions = debugOptions;
        this.fileCollectionFactory = fileCollectionFactory;
        immutableSystemProperties.put(FILE_ENCODING_KEY, Charset.defaultCharset().name());
        immutableSystemProperties.put(USER_LANGUAGE_KEY, DEFAULT_LOCALE.getLanguage());
        immutableSystemProperties.put(USER_COUNTRY_KEY, DEFAULT_LOCALE.getCountry());
        immutableSystemProperties.put(USER_VARIANT_KEY, DEFAULT_LOCALE.getVariant());
    }

    public JvmOptions(FileCollectionFactory fileCollectionFactory) {
        this(fileCollectionFactory, new DefaultJavaDebugOptions());
    }

    /**
     * @return all jvm args including system properties
     */
    public List<String> getAllJvmArgs() {
        List<String> args = new LinkedList<String>();
        formatSystemProperties(getMutableSystemProperties(), args);

        // We have to add these after the system properties so they can override any system properties
        // (identical properties later in the command line override earlier ones)
        args.addAll(getAllImmutableJvmArgs());

        return args;
    }

    protected void formatSystemProperties(Map<String, ?> properties, List<String> args) {
        for (Map.Entry<String, ?> entry : properties.entrySet()) {
            if (entry.getValue() != null && entry.getValue().toString().length() > 0) {
                args.add("-D" + entry.getKey() + "=" + entry.getValue().toString());
            } else {
                args.add("-D" + entry.getKey());
            }
        }
    }

    /**
     * @return all immutable jvm args. It excludes most system properties.
     * Only implicitly immutable system properties like "file.encoding" are included.
     * The result is a subset of options returned by {@link #getAllJvmArgs()}
     */
    public List<String> getAllImmutableJvmArgs() {
        List<String> args = new ArrayList<String>(getJvmArgs());
        args.addAll(getManagedJvmArgs());
        return args;
    }

    /**
     * @return the list of jvm args we manage explicitly, for example, max heaps size or file encoding.
     * The result is a subset of options returned by {@link #getAllImmutableJvmArgs()}
     */
    public List<String> getManagedJvmArgs() {
        List<String> args = new ArrayList<String>();
        if (minHeapSize != null) {
            args.add(XMS_PREFIX + minHeapSize);
        }
        if (maxHeapSize != null) {
            args.add(XMX_PREFIX + maxHeapSize);
        }
        if (bootstrapClasspath != null && !bootstrapClasspath.isEmpty()) {
            args.add(BOOTCLASSPATH_PREFIX + bootstrapClasspath.getAsPath());
        }

        // These are implemented as a system property, but don't really function like one
        // So we include it in this “no system property” set.
        formatSystemProperties(immutableSystemProperties, args);

        if (assertionsEnabled) {
            args.add("-ea");
        }

        if (debugOptions.getEnabled().get()) {
            boolean server = debugOptions.getServer().get();
            boolean suspend = debugOptions.getSuspend().get();
            int port = debugOptions.getPort().get();
            args.add("-agentlib:jdwp=transport=dt_socket,server=" + (server ? 'y' : 'n') + ",suspend=" + (suspend ? 'y' : 'n') + ",address=" + port);
        }
        return args;
    }

    public void setAllJvmArgs(Iterable<?> arguments) {
        mutableSystemProperties.clear();
        minHeapSize = null;
        maxHeapSize = null;
        extraJvmArgs.clear();
        assertionsEnabled = false;
        debugOptions.getEnabled().set(false);
        jvmArgs(arguments);
    }

    public List<String> getJvmArgs() {
        List<String> args = new ArrayList<String>();
        for (Object extraJvmArg : extraJvmArgs) {
            args.add(extraJvmArg.toString());
        }
        return args;
    }

    public void setJvmArgs(Iterable<?> arguments) {
        extraJvmArgs.clear();
        jvmArgs(arguments);
    }

    public void jvmArgs(Iterable<?> arguments) {
        for (Object argument : arguments) {
            String argStr = argument.toString();

            if (argStr.equals("-ea") || argStr.equals("-enableassertions")) {
                assertionsEnabled = true;
            } else if (argStr.equals("-da") || argStr.equals("-disableassertions")) {
                assertionsEnabled = false;
            } else if (argStr.startsWith(XMS_PREFIX)) {
                minHeapSize = argStr.substring(XMS_PREFIX.length());
            } else if (argStr.startsWith(XMX_PREFIX)) {
                maxHeapSize = argStr.substring(XMX_PREFIX.length());
            } else if (argStr.startsWith(BOOTCLASSPATH_PREFIX)) {
                String[] bootClasspath = StringUtils.split(argStr.substring(BOOTCLASSPATH_PREFIX.length()), File.pathSeparatorChar);
                setBootstrapClasspath((Object[]) bootClasspath);
            } else if (argStr.startsWith("-D")) {
                String keyValue = argStr.substring(2);
                int equalsIndex = keyValue.indexOf("=");
                if (equalsIndex == -1) {
                    systemProperty(keyValue, "");
                } else {
                    systemProperty(keyValue.substring(0, equalsIndex), keyValue.substring(equalsIndex + 1));
                }
            } else {
                extraJvmArgs.add(argument);
            }
        }

        List<String> debugArgs = new ArrayList<>();
        for (Object extraJvmArg : extraJvmArgs) {
            String extraJvmArgString = extraJvmArg.toString();
            if (extraJvmArgString.equals("-Xdebug") || extraJvmArgString.startsWith("-Xrunjdwp") || extraJvmArgString.startsWith("-agentlib:jdwp")) {
                debugArgs.add(extraJvmArgString);
            }
        }
        if (!debugArgs.isEmpty() && debugOptions.getEnabled().get()) {
            LOGGER.warn("Debug configuration ignored in favor of the supplied JVM arguments: " + debugArgs);
            debugOptions.getEnabled().set(false);
        }
    }

    public void jvmArgs(Object... arguments) {
        jvmArgs(Arrays.asList(arguments));
    }

    public Map<String, Object> getMutableSystemProperties() {
        return mutableSystemProperties;
    }

    public Map<String, Object> getImmutableSystemProperties() {
        return immutableSystemProperties;
    }

    public void setSystemProperties(Map<String, ?> properties) {
        mutableSystemProperties.clear();
        systemProperties(properties);
    }

    public void systemProperties(Map<String, ?> properties) {
        for (Map.Entry<String, ?> entry : properties.entrySet()) {
            systemProperty(entry.getKey(), entry.getValue());
        }
    }

    public void systemProperty(String name, Object value) {
        if (IMMUTABLE_SYSTEM_PROPERTIES.contains(name)) {
            immutableSystemProperties.put(name, value);
        } else {
            mutableSystemProperties.put(name, value);
        }
    }

    public FileCollection getBootstrapClasspath() {
        return internalGetBootstrapClasspath();
    }

    private ConfigurableFileCollection internalGetBootstrapClasspath() {
        if (bootstrapClasspath == null) {
            bootstrapClasspath = fileCollectionFactory.configurableFiles("bootstrap classpath");
        }
        return bootstrapClasspath;
    }

    public void setBootstrapClasspath(FileCollection classpath) {
        internalGetBootstrapClasspath().setFrom(classpath);
    }

    public void setBootstrapClasspath(Object... classpath) {
        internalGetBootstrapClasspath().setFrom(classpath);
    }

    public void bootstrapClasspath(Object... classpath) {
        internalGetBootstrapClasspath().from(classpath);
    }

    public String getMinHeapSize() {
        return minHeapSize;
    }

    public void setMinHeapSize(String heapSize) {
        this.minHeapSize = heapSize;
    }

    public String getMaxHeapSize() {
        return maxHeapSize;
    }

    public void setMaxHeapSize(String heapSize) {
        this.maxHeapSize = heapSize;
    }

    public String getDefaultCharacterEncoding() {
        return immutableSystemProperties.get(FILE_ENCODING_KEY).toString();
    }

    public void setDefaultCharacterEncoding(String defaultCharacterEncoding) {
        immutableSystemProperties.put(FILE_ENCODING_KEY, GUtil.isTrue(defaultCharacterEncoding) ? defaultCharacterEncoding : Charset.defaultCharset().name());
    }

    public boolean getEnableAssertions() {
        return assertionsEnabled;
    }

    public void setEnableAssertions(boolean enabled) {
        assertionsEnabled = enabled;
    }

    public boolean getDebug() {
        return debugOptions.getEnabled().get();
    }

    public void setDebug(boolean enabled) {
        debugOptions.getEnabled().set(enabled);
    }

    public JavaDebugOptions getDebugOptions() {
        return debugOptions;
    }

    public void copyTo(JavaForkOptions target) {
        target.setJvmArgs(extraJvmArgs);
        target.setSystemProperties(mutableSystemProperties);
        target.setMinHeapSize(minHeapSize);
        target.setMaxHeapSize(maxHeapSize);
        target.bootstrapClasspath(getBootstrapClasspath().getFiles());
        target.setEnableAssertions(assertionsEnabled);
        copyDebugOptionsTo(target.getDebugOptions());
        target.systemProperties(immutableSystemProperties);
    }

    public JvmOptions createCopy() {
        JvmOptions target = new JvmOptions(fileCollectionFactory);
        target.setJvmArgs(extraJvmArgs);
        target.setSystemProperties(mutableSystemProperties);
        target.setMinHeapSize(minHeapSize);
        target.setMaxHeapSize(maxHeapSize);
        if (bootstrapClasspath != null) {
            target.setBootstrapClasspath(getBootstrapClasspath().getFiles());
        }
        target.setEnableAssertions(assertionsEnabled);
        copyDebugOptionsTo(target.getDebugOptions());
        target.systemProperties(immutableSystemProperties);
        return target;
    }

    private void copyDebugOptionsTo(JavaDebugOptions otherOptions) {
        // This severs the connection between from this debugOptions to the other debugOptions
        otherOptions.getEnabled().set(debugOptions.getEnabled().get());
        otherOptions.getPort().set(debugOptions.getPort().get());
        otherOptions.getServer().set(debugOptions.getServer().get());
        otherOptions.getSuspend().set(debugOptions.getSuspend().get());
    }

    public static List<String> fromString(String input) {
        return ArgumentsSplitter.split(input);
    }
}
