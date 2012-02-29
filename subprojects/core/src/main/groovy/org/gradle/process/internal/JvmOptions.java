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

import com.google.common.base.Splitter;
import com.google.common.collect.Lists;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.internal.file.collections.DefaultConfigurableFileCollection;
import org.gradle.process.JavaForkOptions;

import java.io.File;
import java.nio.charset.Charset;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.apache.commons.lang.StringUtils.removeEnd;
import static org.apache.commons.lang.StringUtils.removeStart;

public class JvmOptions {
    private static final Pattern SYS_PROP_PATTERN = Pattern.compile("-D(.+?)=(.*)");
    private static final Pattern DEFAULT_ENCODING_PATTERN = Pattern.compile("-Dfile\\Q.\\Eencoding=(.*)");
    private static final Pattern NO_ARG_SYS_PROP_PATTERN = Pattern.compile("-D([^=]+)");
    private static final Pattern MIN_HEAP_PATTERN = Pattern.compile("-Xms(.+)");
    private static final Pattern MAX_HEAP_PATTERN = Pattern.compile("-Xmx(.+)");
    private static final Pattern BOOTSTRAP_PATTERN = Pattern.compile("-Xbootclasspath:(.+)");

    private final List<Object> extraJvmArgs = new ArrayList<Object>();
    private final Map<String, Object> systemProperties = new TreeMap<String, Object>();
    private DefaultConfigurableFileCollection bootstrapClasspath;
    private String minHeapSize;
    private String maxHeapSize;
    private boolean assertionsEnabled;
    private boolean debug;
    private String defaultCharacterEncoding;

    public JvmOptions(FileResolver resolver) {
        this.bootstrapClasspath = new DefaultConfigurableFileCollection(resolver, null);
    }

    public List<String> getAllJvmArgs() {
        List<String> args = new LinkedList<String>();
        for (Map.Entry<String, Object> entry : getSystemProperties().entrySet()) {
            if (entry.getValue() != null) {
                args.add(String.format("-D%s=%s", entry.getKey(), entry.getValue().toString()));
            } else {
                args.add(String.format("-D%s", entry.getKey()));
            }
        }

        // We have to add these after the system properties so they can override any system properties
        // (identical properties later in the command line override earlier ones)
        args.addAll(getAllImmutableJvmArgs());

        return args;
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
     *          The result is a subset of options returned by {@link #getAllImmutableJvmArgs()}
     */
    public List<String> getManagedJvmArgs() {
        List<String> args = new ArrayList<String>();
        if (minHeapSize != null) {
            args.add(String.format("-Xms%s", minHeapSize));
        }
        if (maxHeapSize != null) {
            args.add(String.format("-Xmx%s", maxHeapSize));
        }
        FileCollection bootstrapClasspath = getBootstrapClasspath();
        if (!bootstrapClasspath.isEmpty()) {
            args.add(String.format("-Xbootclasspath:%s", bootstrapClasspath.getAsPath()));
        }

        // This is implemented as a system property, but doesn't really function like one
        // So we include it in this “no system property” set.
        addDefaultEncodingJvmArg(args);

        if (assertionsEnabled) {
            args.add("-ea");
        }
        if (debug) {
            args.add("-Xdebug");
            args.add("-Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=5005");
        }
        return args;
    }

    public void setAllJvmArgs(Iterable<?> arguments) {
        systemProperties.clear();
        minHeapSize = null;
        maxHeapSize = null;
        extraJvmArgs.clear();
        assertionsEnabled = false;
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

            Matcher matcher = DEFAULT_ENCODING_PATTERN.matcher(argStr);
            if (matcher.matches()) {
                defaultCharacterEncoding = matcher.group(1);
                continue;
            }
            matcher = SYS_PROP_PATTERN.matcher(argStr);
            if (matcher.matches()) {
                systemProperties.put(matcher.group(1), matcher.group(2));
                continue;
            }
            matcher = NO_ARG_SYS_PROP_PATTERN.matcher(argStr);
            if (matcher.matches()) {
                systemProperties.put(matcher.group(1), "");
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
                setBootstrapClasspath(matcher.group(1).split(Pattern.quote(File.pathSeparator)));
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
        Set<Object> matches = new HashSet<Object>();
        for (Object extraJvmArg : extraJvmArgs) {
            if (extraJvmArg.toString().equals("-Xdebug")) {
                xdebugFound = true;
                matches.add(extraJvmArg);
            } else if (extraJvmArg.toString().equals("-Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=5005")) {
                xrunjdwpFound = true;
                matches.add(extraJvmArg);
            }
        }
        if (xdebugFound && xrunjdwpFound) {
            debug = true;
            extraJvmArgs.removeAll(matches);
        } else {
            debug = false;
        }
    }

    public void jvmArgs(Object... arguments) {
        jvmArgs(Arrays.asList(arguments));
    }

    public Map<String, Object> getSystemProperties() {
        return systemProperties;
    }

    public void setSystemProperties(Map<String, ?> properties) {
        systemProperties.clear();
        systemProperties.putAll(properties);
    }

    public void systemProperties(Map<String, ?> properties) {
        systemProperties.putAll(properties);
    }

    public void systemProperty(String name, Object value) {
        systemProperties.put(name, value);
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
        return defaultCharacterEncoding;
    }

    public String getEffectiveDefaultCharacterEncoding() {
        if (defaultCharacterEncoding != null) {
            return defaultCharacterEncoding;
        } else {
            return Charset.defaultCharset().name();
        }
    }

    private void addDefaultEncodingJvmArg(List<String> jvmArgs) {
        // The “file.encoding” system property is not part of the JVM standard, but both the
        // Sun and IBM JVMs support this system property. We should at some point abstract this
        // behind the Jvm class.
        jvmArgs.add(String.format("-Dfile.encoding=%s", getEffectiveDefaultCharacterEncoding()));
    }

    public void setDefaultCharacterEncoding(String defaultCharacterEncoding) {
        this.defaultCharacterEncoding = defaultCharacterEncoding;
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
        target.setSystemProperties(systemProperties);
        target.setMinHeapSize(minHeapSize);
        target.setMaxHeapSize(maxHeapSize);
        target.setBootstrapClasspath(bootstrapClasspath);
        target.setEnableAssertions(assertionsEnabled);
        target.setDebug(debug);
    }

    public static List<String> fromString(String input) {
        //split on whitespace but only if it's not inside double quotes
        List<String> split = Lists.newArrayList(Splitter.onPattern("\\s(?=([^\"]*\"[^\"]*\")*[^\"]*$)")
                .omitEmptyStrings()
                .split(input));
        
        //now lets get rid of the value quotes, e.g "-Dfoo=bar" -> -Dfoo=bar, -Dfoo="bar" -> -Dfoo=bar, -XXfoo="baz" -> -XXfoo=baz
        List<String> out = new ArrayList<String>();
        for (String s : split) {
            if (s.startsWith("\"") && s.endsWith("\"")) {
                //remove trailing quotes
                s = removeStart(s, "\"");
                s = removeEnd(s, "\"");
            }
            if (s.matches("(?s)[^\"]+=\".*\"(?s)")) {
                //remove trailing quotes from value
                s = s.replaceFirst("=\"", "=");
                s = removeEnd(s, "\"");
            }
            out.add(s);
        }
        
        return out;
    }
}
