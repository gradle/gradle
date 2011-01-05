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
package org.gradle.process.internal;


import org.gradle.api.file.FileCollection
import org.gradle.api.internal.file.FileResolver
import org.gradle.api.internal.file.IdentityFileResolver
import org.gradle.api.internal.file.PathResolvingFileCollection
import org.gradle.util.Jvm
import spock.lang.Specification
import static java.util.Arrays.asList

public class JavaExecHandleBuilderTest extends Specification {
    FileResolver fileResolver = new IdentityFileResolver()
    JavaExecHandleBuilder builder = new JavaExecHandleBuilder(fileResolver)
    
    public void cannotSetAllJvmArgs() {
        when:
        builder.setAllJvmArgs(asList("arg"))

        then:
        thrown(UnsupportedOperationException)
    }

    public void buildsCommandLineForJavaProcess() {
        File jar1 = new File("file1.jar").canonicalFile
        File jar2 = new File("file2.jar").canonicalFile

        FileCollection classpath = new PathResolvingFileCollection(fileResolver, null, jar1, jar2)

        builder.main = 'mainClass'
        builder.args("arg1", "arg2")
        builder.jvmArgs("jvm1", "jvm2")
        builder.classpath(jar1, jar2)
        builder.systemProperty("prop", "value")

        when:
        List jvmArgs = builder.getAllJvmArgs()

        then:
        jvmArgs == ['jvm1', 'jvm2', '-Dprop=value', '-cp', "$jar1$File.pathSeparator$jar2"]

        when:
        List commandLine = builder.getCommandLine()

        then:
        String executable = Jvm.current().getJavaExecutable().getAbsolutePath()
        commandLine == [executable, 'jvm1', 'jvm2', '-Dprop=value', '-cp', "$jar1$File.pathSeparator$jar2",
                'mainClass', 'arg1', 'arg2']
    }
}
