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
package org.gradle.util.exec;

import org.gradle.util.GUtil;
import org.gradle.util.Jvm;

import java.io.File;
import java.util.*;

public class JavaExecHandleBuilder {
    private String mainClass;
    private final List<String> arguments = new ArrayList<String>();
    private final List<String> extraJvmArguments = new ArrayList<String>();
    private final List<String> jvmArguments = new ArrayList<String>();
    private final Set<File> classpath = new LinkedHashSet<File>();
    private final ExecHandleBuilder execHandleBuilder = new ExecHandleBuilder();

    public JavaExecHandleBuilder() {
        execHandleBuilder.execCommand(Jvm.current().getJavaExecutable());
    }

    public String getMainClass() {
        return mainClass;
    }

    public JavaExecHandleBuilder mainClass(String mainClassName) {
        this.mainClass = mainClassName;
        setArgs();
        return this;
    }

    public List<String> getArguments() {
        return arguments;
    }

    public JavaExecHandleBuilder arguments(String... args) {
        arguments(Arrays.asList(args));
        return this;
    }

    private JavaExecHandleBuilder arguments(Collection<String> argsList) {
        arguments.addAll(argsList);
        setArgs();
        return this;
    }

    public List<String> getJvmArguments() {
        return jvmArguments;
    }

    public JavaExecHandleBuilder jvmArguments(String... args) {
        jvmArguments(Arrays.asList(args));
        return this;
    }

    public JavaExecHandleBuilder jvmArguments(Collection<String> args) {
        extraJvmArguments.addAll(args);
        setArgs();
        return this;
    }

    private void setArgs() {
        jvmArguments.clear();
        if (!classpath.isEmpty()) {
            jvmArguments.add("-cp");
            jvmArguments.add(GUtil.join(classpath, File.pathSeparator));
        }
        jvmArguments.addAll(extraJvmArguments);

        execHandleBuilder.setArguments(GUtil.addLists(jvmArguments, Collections.singleton(mainClass), arguments));
    }

    public JavaExecHandleBuilder execDirectory(File execDir) {
        execHandleBuilder.execDirectory(execDir);
        return this;
    }

    public JavaExecHandleBuilder classpath(File... classpath) {
        classpath(Arrays.asList(classpath));
        return this;
    }
    
    public JavaExecHandleBuilder classpath(Collection<File> classpath) {
        this.classpath.addAll(classpath);
        setArgs();
        return this;
    }

    public Set<File> getClasspath() {
        return classpath;
    }

    public ExecHandleBuilder getCommand() {
        return execHandleBuilder;
    }

    public ExecHandle build() {
        if (mainClass == null) {
            throw new IllegalStateException("No main class specified");
        }
        return getCommand().build();
    }
}
