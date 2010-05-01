/*
 * Copyright 2010 the original author or authors.
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

package org.gradle.api.internal.tasks.util;

import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.util.Jvm;
import org.gradle.util.exec.JavaForkOptions;

import java.io.File;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DefaultJavaForkOptions extends DefaultProcessForkOptions implements JavaForkOptions {
    private final Pattern sysPropPattern = Pattern.compile("-D(.+?)=(.*)");
    private final Pattern noArgSysPropPattern = Pattern.compile("-D([^=]+)");
    private final Pattern maxHeapPattern = Pattern.compile("-Xmx(.+)");
    private final Pattern bootstrapPattern = Pattern.compile("-Xbootclasspath:(.+)");
    private final List<Object> extraJvmArgs = new ArrayList<Object>();
    private final Map<String, Object> systemProperties = new TreeMap<String, Object>();
    private final List<Object> bootstrapClasspath = new ArrayList<Object>();
    private String maxHeapSize;
    private boolean assertionsEnabled;

    public DefaultJavaForkOptions(FileResolver resolver) {
        this(resolver, Jvm.current());
    }

    public DefaultJavaForkOptions(FileResolver resolver, Jvm jvm) {
        super(resolver);
        setExecutable(jvm.getJavaExecutable());
    }

    public List<String> getAllJvmArgs() {
        List<String> args = new ArrayList<String>();
        args.addAll(getJvmArgs());
        for (Map.Entry<String, Object> entry : getSystemProperties().entrySet()) {
            if (entry.getValue() != null) {
                args.add(String.format("-D%s=%s", entry.getKey(), entry.getValue().toString()));
            } else {
                args.add(String.format("-D%s", entry.getKey()));
            }
        }
        if (maxHeapSize != null) {
            args.add(String.format("-Xmx%s", maxHeapSize));
        }
        FileCollection bootstrapClasspath = getBootstrapClasspath();
        if (!bootstrapClasspath.isEmpty()) {
            args.add(String.format("-Xbootclasspath:%s", bootstrapClasspath.getAsPath()));
        }
        if (assertionsEnabled) {
            args.add("-ea");
        }
        return args;
    }

    public void setAllJvmArgs(Iterable<?> arguments) {
        systemProperties.clear();
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

    public JavaForkOptions jvmArgs(Iterable<?> arguments) {
        for (Object argument : arguments) {
            String argStr = argument.toString();
            Matcher matcher = sysPropPattern.matcher(argStr);
            if (matcher.matches()) {
                systemProperties.put(matcher.group(1), matcher.group(2));
                continue;
            }
            matcher = noArgSysPropPattern.matcher(argStr);
            if (matcher.matches()) {
                systemProperties.put(matcher.group(1), null);
                continue;
            }
            matcher = maxHeapPattern.matcher(argStr);
            if (matcher.matches()) {
                maxHeapSize = matcher.group(1);
                continue;
            }
            matcher = bootstrapPattern.matcher(argStr);
            if (matcher.matches()) {
                setBootstrapClasspath(Arrays.asList(matcher.group(1).split(Pattern.quote(File.pathSeparator))));
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
        return this;
    }

    public JavaForkOptions jvmArgs(Object... arguments) {
        jvmArgs(Arrays.asList(arguments));
        return this;
    }

    public Map<String, Object> getSystemProperties() {
        return systemProperties;
    }

    public void setSystemProperties(Map<String, ?> properties) {
        systemProperties.clear();
        systemProperties.putAll(properties);
    }

    public JavaForkOptions systemProperties(Map<String, ?> properties) {
        systemProperties.putAll(properties);
        return this;
    }

    public JavaForkOptions systemProperty(String name, Object value) {
        systemProperties.put(name, value);
        return this;
    }

    public FileCollection getBootstrapClasspath() {
        return getResolver().resolveFiles(bootstrapClasspath);
    }

    public void setBootstrapClasspath(Iterable<?> classpath) {
        bootstrapClasspath.clear();
        bootstrapClasspath(classpath);
    }

    public JavaForkOptions bootstrapClasspath(Iterable<?> classpath) {
        for (Object item : classpath) {
            bootstrapClasspath.add(item);
        }
        return this;
    }

    public JavaForkOptions bootstrapClasspath(Object... classpath) {
        bootstrapClasspath(Arrays.asList(classpath));
        return this;
    }

    public String getMaxHeapSize() {
        return maxHeapSize;
    }

    public void setMaxHeapSize(String heapSize) {
        this.maxHeapSize = heapSize;
    }

    public boolean getEnableAssertions() {
        return assertionsEnabled;
    }

    public void setEnableAssertions(boolean enabled) {
        assertionsEnabled = enabled;
    }

    public JavaForkOptions copyTo(JavaForkOptions target) {
        super.copyTo(target);
        target.setJvmArgs(extraJvmArgs);
        target.setSystemProperties(systemProperties);
        target.setMaxHeapSize(maxHeapSize);
        target.setBootstrapClasspath(bootstrapClasspath);
        target.setEnableAssertions(assertionsEnabled);
        return this;
    }
}
