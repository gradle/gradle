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

import org.gradle.api.internal.file.AbstractFileCollection
import org.gradle.api.internal.file.FileCollectionFactory
import org.gradle.api.internal.file.TestFiles
import org.gradle.api.internal.file.temp.TemporaryFileProvider
import org.gradle.initialization.DefaultBuildCancellationToken
import org.gradle.internal.jvm.JavaModuleDetector
import org.gradle.internal.jvm.Jvm
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.TestUtil
import org.junit.Rule
import spock.lang.Issue
import spock.lang.Specification

import java.nio.charset.Charset
import java.util.concurrent.Executor

class JavaExecHandleBuilderTest extends Specification {
    @Rule
    final TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider(getClass())
    final TemporaryFileProvider temporaryFileProvider = TestFiles.tmpDirTemporaryFileProvider(tmpDir.testDirectory)
    JavaExecHandleBuilder builder = new JavaExecHandleBuilder(
        TestFiles.resolver(),
        TestFiles.fileCollectionFactory(),
        TestUtil.objectFactory(),
        Mock(Executor),
        new DefaultBuildCancellationToken(),
        temporaryFileProvider,
        null,
        TestFiles.execFactory().newJavaForkOptions()
    )

    FileCollectionFactory fileCollectionFactory = TestFiles.fileCollectionFactory()

    def "builds commandLine for Java process - input encoding #inputEncoding"() {
        File jar1 = new File("file1.jar").canonicalFile
        File jar2 = new File("file2.jar").canonicalFile

        builder.mainClass.set('mainClass')
        builder.args("arg1", "arg2")
        builder.jvmArgs("jvm1", "jvm2")
        builder.classpath(jar1, jar2)
        builder.systemProperty("prop", "value")
        builder.minHeapSize.set("64m")
        builder.maxHeapSize.set("1g")
        builder.defaultCharacterEncoding.set(inputEncoding)

        when:
        List jvmArgs = builder.getAllJvmArgs().get()

        then:
        jvmArgs == ['-Dprop=value', 'jvm1', 'jvm2', '-Xms64m', '-Xmx1g', fileEncodingProperty(expectedEncoding), *localeProperties(), '-cp', "$jar1$File.pathSeparator$jar2", "mainClass"]

        when:
        List commandLine = builder.getCommandLine()

        then:
        String executable = Jvm.current().getJavaExecutable().getAbsolutePath()
        commandLine == [executable, '-Dprop=value', 'jvm1', 'jvm2', '-Xms64m', '-Xmx1g', fileEncodingProperty(expectedEncoding), *localeProperties(), '-cp', "$jar1$File.pathSeparator$jar2", 'mainClass', 'arg1', 'arg2']

        where:
        inputEncoding | expectedEncoding
        null          | Charset.defaultCharset().name()
        "UTF-16"      | "UTF-16"
    }

    def "can append to classpath"() {
        given:
        File jar1 = new File("file1.jar").canonicalFile
        File jar2 = new File("file2.jar").canonicalFile

        builder.classpath(jar1)

        when:
        builder.classpath(jar2)

        then:
        builder.classpath.contains(jar1)
        builder.classpath.contains(jar2)
    }

    def "can replace classpath"() {
        given:
        File jar1 = new File("file1.jar").canonicalFile
        File jar2 = new File("file2.jar").canonicalFile

        builder.classpath(jar1)

        when:
        builder.setClasspath(fileCollectionFactory.resolving(jar2))

        then:
        !builder.classpath.contains(jar1)
        builder.classpath.contains(jar2)
    }

    @Issue("gradle/gradle#8748")
    def "can prepend to classpath"() {
        given:
        File jar1 = new File("file1.jar").canonicalFile
        File jar2 = new File("file2.jar").canonicalFile

        builder.mainClass.set("main")
        builder.classpath(jar1)

        when:
        builder.setClasspath(fileCollectionFactory.resolving([jar2, builder.getClasspath()]))

        then:
        builder.commandLine.contains("$jar2$File.pathSeparator$jar1".toString())
    }

    def "can be used without module detector service"() {
        given:
        builder.mainModule.set("mainModule")
        builder.mainClass.set("mainClass")
        builder.classpath(new File("file1.jar").canonicalFile)

        when:
        // turn off module support:
        builder.modularity.inferModulePath.set(false)

        then:
        !builder.getAllArguments().contains('--module')
    }

    def "throws reasonable error if module support is turned on without module detector service"() {
        given:
        builder.mainModule.set("mainModule")
        builder.mainClass.set("mainClass")
        builder.classpath(new File("file1.jar").canonicalFile)

        when:
        builder.modularity.inferModulePath.set(true)
        builder.getAllArguments()

        then:
        // This is an internal error. If the builder is used through public API, the detection service is always available
        def e = thrown(IllegalStateException)
        e.message == 'Running a Java module is not supported in this context.'
    }

    def "supports module path"() {
        given:
        File libJar = new File("lib.jar")
        File moduleJar = new File("module.jar")
        JavaModuleDetector moduleDetector = Mock(JavaModuleDetector) {
            inferModulePath(_, _) >> new AbstractFileCollection() {
                String getDisplayName() { '' }

                Set<File> getFiles() { [moduleJar] }
            }
            inferClasspath(_, _) >> new AbstractFileCollection() {
                String getDisplayName() { '' }

                Set<File> getFiles() { [libJar] }
            }
        }
        builder = new JavaExecHandleBuilder(
            TestFiles.resolver(),
            TestFiles.fileCollectionFactory(),
            TestUtil.objectFactory(),
            Mock(Executor),
            new DefaultBuildCancellationToken(),
            temporaryFileProvider,
            moduleDetector,
            TestFiles.execFactory().newJavaForkOptions()
        )

        builder.mainModule.set("mainModule")
        builder.mainClass.set("mainClass")
        builder.classpath(libJar, moduleJar)

        when:
        builder.modularity.inferModulePath.set(true)

        then:
        builder.getAllArguments().findAll { !it.startsWith('-Duser.') } == ['-Dfile.encoding=UTF-8', '-cp', libJar.name, '--module-path', moduleJar.name, '--module', 'mainModule/mainClass']
    }

    def "detects null entries early"() {
        when:
        builder.args(1, null)
        then:
        thrown(IllegalArgumentException)
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
