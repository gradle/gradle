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
package org.gradle.process.internal

import org.gradle.api.internal.file.TestFiles
import org.gradle.initialization.DefaultBuildCancellationToken
import org.gradle.internal.jvm.Jvm
import spock.lang.Specification
import spock.lang.Unroll

import java.nio.charset.Charset
import java.util.concurrent.Executor

import static java.util.Arrays.asList

class JavaExecHandleBuilderTest extends Specification {
    JavaExecHandleBuilder builder = new JavaExecHandleBuilder(TestFiles.resolver(), Mock(Executor), new DefaultBuildCancellationToken())

    public void cannotSetAllJvmArgs() {
        when:
        builder.setAllJvmArgs(asList("arg"))

        then:
        thrown(UnsupportedOperationException)
    }

    @Unroll("buildsCommandLineForJavaProcess - input encoding #inputEncoding")
    public void buildsCommandLineForJavaProcess() {
        File jar1 = new File("file1.jar").canonicalFile
        File jar2 = new File("file2.jar").canonicalFile

        builder.main = 'mainClass'
        builder.args("arg1", "arg2")
        builder.jvmArgs("jvm1", "jvm2")
        builder.classpath(jar1, jar2)
        builder.systemProperty("prop", "value")
        builder.minHeapSize = "64m"
        builder.maxHeapSize = "1g"
        builder.defaultCharacterEncoding = inputEncoding

        when:
        List jvmArgs = builder.getAllJvmArgs()

        then:
        jvmArgs == ['-Dprop=value', 'jvm1', 'jvm2', '-Xms64m', '-Xmx1g', fileEncodingProperty(expectedEncoding), *localeProperties(), '-cp', "$jar1$File.pathSeparator$jar2"]

        when:
        List commandLine = builder.getCommandLine()

        then:
        String executable = Jvm.current().getJavaExecutable().getAbsolutePath()
        commandLine == [executable,  '-Dprop=value', 'jvm1', 'jvm2', '-Xms64m', '-Xmx1g', fileEncodingProperty(expectedEncoding), *localeProperties(), '-cp', "$jar1$File.pathSeparator$jar2", 'mainClass', 'arg1', 'arg2']

        where:
        inputEncoding | expectedEncoding
        null          | Charset.defaultCharset().name()
        "UTF-16"      | "UTF-16"
    }

    def "detects null entries early"() {
        when: builder.args(1, null)
        then: thrown(IllegalArgumentException)
    }

    private String fileEncodingProperty(String encoding = Charset.defaultCharset().name()) {
        return "-Dfile.encoding=$encoding"
    }

    private static List<String> localeProperties(Locale locale = Locale.default) {
        ["country", "language", "variant"].sort().collectEntries {
            ["user.$it", locale."$it"]
        }.collect {
            it.value ? "-D$it.key=$it.value" : "-D$it.key"
        }
    }
}
