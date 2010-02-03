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

import org.gradle.util.Jvm;
import org.junit.Test;

import java.io.File;
import java.util.List;

import static java.util.Arrays.*;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

public class JavaExecHandleBuilderTest {
    private final JavaExecHandleBuilder builder = new JavaExecHandleBuilder();

    @Test(expected = UnsupportedOperationException.class)
    public void cannotSetProcessArguments() {
        builder.arguments("extra arg");
    }

    @Test(expected = UnsupportedOperationException.class)
    public void cannotSetAllJvmArgs() {
        builder.setAllJvmArgs(asList("arg"));
    }

    @Test
    public void buildsCommandLineForJavaProcess() {
        File jar1 = new File("file1.jar");
        File jar2 = new File("file2.jar");

        builder.mainClass("mainClass");
        builder.applicationArgs("arg1", "arg2");
        builder.jvmArgs("jvm1", "jvm2");
        builder.classpath(jar1, jar2);
        builder.systemProperty("prop", "value");

        List<String> jvmArgs = builder.getAllJvmArgs();
        assertThat(jvmArgs.size(), equalTo(5));
        assertThat(jvmArgs.get(0), equalTo("jvm1"));
        assertThat(jvmArgs.get(1), equalTo("jvm2"));
        assertThat(jvmArgs.get(2), equalTo("-Dprop=value"));
        assertThat(jvmArgs.get(3), equalTo("-cp"));
        assertThat(jvmArgs.get(4), equalTo(jar1 + File.pathSeparator + jar2));

        List<String> commandLine = builder.getCommandLine();
        assertThat(commandLine.size(), equalTo(9));
        assertThat(commandLine.get(0), equalTo(Jvm.current().getJavaExecutable().getAbsolutePath()));
        assertThat(commandLine.get(1), equalTo("jvm1"));
        assertThat(commandLine.get(2), equalTo("jvm2"));
        assertThat(commandLine.get(3), equalTo("-Dprop=value"));
        assertThat(commandLine.get(4), equalTo("-cp"));
        assertThat(commandLine.get(5), equalTo(jar1 + File.pathSeparator + jar2));
        assertThat(commandLine.get(6), equalTo("mainClass"));
        assertThat(commandLine.get(7), equalTo("arg1"));
        assertThat(commandLine.get(8), equalTo("arg2"));
    }
}
