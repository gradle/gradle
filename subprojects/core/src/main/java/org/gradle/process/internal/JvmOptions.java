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
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.file.collections.DefaultConfigurableFileCollection;
import org.gradle.internal.file.PathToFileResolver;
import org.gradle.process.JavaForkOptions;
import org.gradle.util.GUtil;
import org.gradle.util.internal.ArgumentsSplitter;

import java.io.File;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class JvmOptions {

    private static final Pattern SYS_PROP_PATTERN = Pattern.compile("(?s)-D(.+?)=(.*)");
    private static final Pattern NO_ARG_SYS_PROP_PATTERN = Pattern.compile("-D([^=]+)");
    private static final Pattern MIN_HEAP_PATTERN = Pattern.compile("-Xms(.+)");
    private static final Pattern MAX_HEAP_PATTERN = Pattern.compile("-Xmx(.+)");
    private static final Pattern BOOTSTRAP_PATTERN = Pattern.compile("-Xbootclasspath:(.+)");
    public static final String FILE_ENCODING_KEY = "file.encoding";
    public static final String USER_LANGUAGE_KEY = "user.language";
    public static final String USER_COUNTRY_KEY = "user.country";
    public static final String USER_VARIANT_KEY = "user.variant";
    public static final String JMX_REMOTE_KEY = "com.sun.management.jmxremote";
    public static final String JAVA_IO_TMPDIR_KEY = "java.io.tmpdir";

    public static final Set<String> IMMUTABLE_SYSTEM_PROPERTIES = ImmutableSet.of(
        FILE_ENCODING_KEY, USER_LANGUAGE_KEY, USER_COUNTRY_KEY, USER_VARIANT_KEY, JMX_REMOTE_KEY, JAVA_IO_TMPDIR_KEY
    );

    // Store this because Locale.default is mutable and we want the unchanged default
    // We are assuming this class will be initialized before any code has a chance to change the default
    private static final Locale DEFAULT_LOCALE = Locale.getDefault();

    private final List<Object> extraJvmArgs = new ArrayList<Object>();
    private final Map<String, Object> mutableSystemProperties = new TreeMap<String, Object>();

    private DefaultConfigurableFileCollection bootstrapClasspath;
    private String minHeapSize;
    private String maxHeapSize;
    private boolean assertionsEnabled;
    private boolean debug;

    protected final Map<String, Object> immutableSystemProperties = new TreeMap<String, Object>();

    public JvmOptions(PathToFileResolver resolver) {
        this.bootstrapClasspath = new DefaultConfigurableFileCollection(resolver, null);
        immutableSystemProperties.put(FILE_ENCODING_KEY, Charset.defaultCharset().name());
        immutableSystemProperties.put(USER_LANGUAGE_KEY, DEFAULT_LOCALE.getLanguage());
        immutableSystemProperties.put(USER_COUNTRY_KEY, DEFAULT_LOCALE.getCountry());
        immutableSystemProperties.put(USER_VARIANT_KEY, DEFAULT_LOCALE.getVariant());
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
        List<String> args = new ArrayList<String>();
        args.addAll(getJvmArgs());
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
            args.add("-Xms" + minHeapSize);
        }
        if (maxHeapSize != null) {
            args.add("-Xmx" + maxHeapSize);
        }
        FileCollection bootstrapClasspath = getBootstrapClasspath();
        if (!bootstrapClasspath.isEmpty()) {
            args.add("-Xbootclasspath:" + bootstrapClasspath.getAsPath());
        }

        // These are implemented as a system property, but don't really function like one
        // So we include it in this “no system property” set.
        formatSystemProperties(immutableSystemProperties, args);

        if (assertionsEnabled) {
            args.add("-ea");
        }
        if (debug) {
            args.add("-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=5005");
        }
        return args;
    }

    public void setAllJvmArgs(Iterable<?> arguments) {
        mutableSystemProperties.clear();
        minHeapSize = null;
        maxHeapSize = null;
        extraJvmArgs.clear();
        assertionsEnabled = false;
        debug = false;
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

            Matcher matcher = SYS_PROP_PATTERN.matcher(argStr);
            if (matcher.matches()) {
                systemProperty(matcher.group(1), matcher.group(2));
                continue;
            }
            matcher = NO_ARG_SYS_PROP_PATTERN.matcher(argStr);
            if (matcher.matches()) {
                systemProperty(matcher.group(1), "");
                continue;
            }
            matcher = MIN_HEAP_PATTERN.matcher(argStr);
            if (matcher.matches()) {
                minHeapSize = matcher.group(1);
                continue;
            }
            matcher = MAX_HEAP_PATTERN.matcher(argStr);
            if (matcher.matches()) {
                maxHeapSize = matcher.group(1);
                continue;
            }
            matcher = BOOTSTRAP_PATTERN.matcher(argStr);
            if (matcher.matches()) {
                setBootstrapClasspath((Object[]) matcher.group(1).split(Pattern.quote(File.pathSeparator)));
                continue;
            }
            if (argStr.equals("-ea") || argStr.equals("-enableassertions")) {
                assertionsEnabled = true;
                continue;
            }
            if (argStr.equals("-da") || argStr.equals("-disableassertions")) {
                assertionsEnabled = false;
                continue;
            }

            extraJvmArgs.add(argument);
        }

        boolean xdebugFound = false;
        boolean xrunjdwpFound = false;
        boolean xagentlibJdwpFound = false;
        Set<Object> matches = new HashSet<Object>();
        for (Object extraJvmArg : extraJvmArgs) {
            if (extraJvmArg.toString().equals("-Xdebug")) {
                xdebugFound = true;
                matches.add(extraJvmArg);
            } else if (extraJvmArg.toString().equals("-Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=5005")) {
                xrunjdwpFound = true;
                matches.add(extraJvmArg);
            } else if (extraJvmArg.toString().equals("-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=5005")) {
                xagentlibJdwpFound = true;
                matches.add(extraJvmArg);
            }
        }
        if (xdebugFound && xrunjdwpFound || xagentlibJdwpFound) {
            debug = true;
            extraJvmArgs.removeAll(matches);
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
        return bootstrapClasspath;
    }

    public void setBootstrapClasspath(FileCollection classpath) {
        this.bootstrapClasspath.setFrom(classpath);
    }

    public void setBootstrapClasspath(Object... classpath) {
        this.bootstrapClasspath.setFrom(classpath);
    }

    public void bootstrapClasspath(Object... classpath) {
        this.bootstrapClasspath.from(classpath);
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
        return debug;
    }

    public void setDebug(boolean enabled) {
        debug = enabled;
    }

    public void copyTo(JavaForkOptions target) {
        target.setJvmArgs(extraJvmArgs);
        target.setSystemProperties(mutableSystemProperties);
        target.setMinHeapSize(minHeapSize);
        target.setMaxHeapSize(maxHeapSize);
        target.setBootstrapClasspath(bootstrapClasspath);
        target.setEnableAssertions(assertionsEnabled);
        target.setDebug(debug);
        target.systemProperties(immutableSystemProperties);
    }

    public static List<String> fromString(String input) {
        return ArgumentsSplitter.split(input);
    }
}
