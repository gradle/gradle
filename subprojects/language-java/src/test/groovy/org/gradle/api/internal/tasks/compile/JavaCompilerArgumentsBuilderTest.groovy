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
package org.gradle.api.internal.tasks.compile

import org.gradle.api.InvalidUserDataException
import org.gradle.api.JavaVersion
import org.gradle.api.internal.file.TestFiles
import org.gradle.api.tasks.compile.CompileOptions
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.GUtil
import org.gradle.util.TestUtil
import org.junit.Rule
import spock.lang.Specification
import spock.lang.Unroll

import static org.gradle.api.internal.tasks.compile.JavaCompilerArgumentsBuilder.USE_UNSHARED_COMPILER_TABLE_OPTION

class JavaCompilerArgumentsBuilderTest extends Specification {
    @Rule
    TestNameTestDirectoryProvider tempDir = new TestNameTestDirectoryProvider(getClass())

    def defaultOptionsWithoutClasspath = ["-g", "-sourcepath", "", "-proc:none", USE_UNSHARED_COMPILER_TABLE_OPTION]
    def defaultOptions = ["-g", "-sourcepath", "", "-proc:none", USE_UNSHARED_COMPILER_TABLE_OPTION]

    def spec = new DefaultJavaCompileSpec()
    def builder = new JavaCompilerArgumentsBuilder(spec)

    def setup() {
        spec.tempDir = tempDir.file("tmp")
        spec.compileOptions = new CompileOptions(TestUtil.objectFactory())
    }

    def "generates options for an unconfigured spec"() {
        expect:
        builder.build() == defaultOptions
    }

    def "generates -source option when current Jvm Version is used"() {
        spec.sourceCompatibility = JavaVersion.current().toString()

        expect:
        builder.build() == ["-source", JavaVersion.current().toString()] + defaultOptions
    }

    def "generates -source option when compatibility differs from current Jvm version"() {
        spec.sourceCompatibility = "1.6"

        expect:
        builder.build() == ["-source", "1.6"] + defaultOptions
    }

    def "does not include processor options when source compatibility is Java 5 or lower"() {
        spec.sourceCompatibility = "1.5"

        expect:
        builder.build() == ["-source", "1.5", "-g", "-sourcepath", "", USE_UNSHARED_COMPILER_TABLE_OPTION]
    }

    def "generates -target option when current Jvm Version is used"() {
        spec.targetCompatibility = JavaVersion.current().toString()

        expect:
        builder.build() == ["-target", JavaVersion.current().toString()] + defaultOptions
    }

    def "generates -target option when compatibility differs current Jvm version"() {
        spec.targetCompatibility = "1.4"

        expect:
        builder.build() == ["-target", "1.4"] + defaultOptions
    }

    def "removes -source and -target option if --release is present"() {
        when:
        spec.compileOptions.compilerArgs += ['--release', '7']
        spec.sourceCompatibility = '1.7'
        spec.targetCompatibility = '1.7'

        then:
        builder.build() == defaultOptions + ['--release', '7']
    }

    def "removes -source and -target option if release property is set"() {
        when:
        spec.release = 7
        spec.sourceCompatibility = '1.7'
        spec.targetCompatibility = '1.7'

        then:
        builder.build() == ['--release', '7'] + defaultOptions
    }

    def "generates -d option"() {
        def file = new File("/project/build")
        spec.destinationDir = file

        expect:
        builder.build() == ["-d", file.path] + defaultOptions
    }

    def "generates -verbose option"() {
        when:
        spec.compileOptions.verbose = true

        then:
        builder.build() == ["-verbose"] + defaultOptions

        when:
        spec.compileOptions.verbose = false

        then:
        builder.build() == defaultOptions
    }

    def "generates -deprecation option"() {
        when:
        spec.compileOptions.deprecation = true

        then:
        builder.build() == ["-deprecation"] + defaultOptions

        when:
        spec.compileOptions.deprecation = false

        then:
        builder.build() == defaultOptions
    }

    def "generates -nowarn option"() {
        when:
        spec.compileOptions.warnings = true

        then:
        builder.build() == defaultOptions

        when:
        spec.compileOptions.warnings = false

        then:
        builder.build() == ["-nowarn"] + defaultOptions
    }

    def "generates -g option"() {
        when:
        spec.compileOptions.debug = true

        then:
        builder.build() == defaultOptions

        when:
        spec.compileOptions.debugOptions.debugLevel = "source,vars"

        then:
        builder.build() == ["-g:source,vars"] + defaultOptions.findAll { it != "-g" }

        when:
        spec.compileOptions.debug = false

        then:
        builder.build() == ["-g:none"] + defaultOptions.findAll { it != "-g" }
    }

    def "generates -encoding option"() {
        spec.compileOptions.encoding = "some-encoding"

        expect:
        builder.build() == ["-encoding", "some-encoding"] + defaultOptions
    }

    def "generates -bootclasspath option"() {
        def compileOptions = new CompileOptions(TestUtil.objectFactory())
        compileOptions.bootstrapClasspath = TestFiles.fixed(new File("lib1.jar"), new File("lib2.jar"))
        spec.compileOptions = compileOptions

        expect:
        builder.build() == ["-bootclasspath", "lib1.jar${File.pathSeparator}lib2.jar"] + defaultOptions
    }

    def "generates -extdirs option"() {
        spec.compileOptions.extensionDirs = "/dir1:/dir2"

        expect:
        builder.build() == ["-extdirs", "/dir1:/dir2"] + defaultOptions
    }

    def "generates -classpath option"() {
        def file1 = new File("/lib/lib1.jar")
        def file2 = new File("/lib/lib2.jar")
        spec.compileClasspath = [file1, file2]

        expect:
        builder.build() == defaultOptionsWithoutClasspath + ["-classpath", "$file1$File.pathSeparator$file2"]
    }

    def "generates -processorpath option"() {
        def file1 = new File("/lib/lib1.jar")
        def file2 = new File("/lib/lib2.jar")
        spec.annotationProcessorPath = [file1, file2]

        expect:
        builder.build() == ["-g", "-sourcepath", "", "-processorpath", "$file1$File.pathSeparator$file2", USE_UNSHARED_COMPILER_TABLE_OPTION]
    }

    def "generates -s option"() {
        def outputDir = new File("build/generated-sources")
        spec.compileOptions.annotationProcessorGeneratedSourcesDirectory = outputDir

        expect:
        builder.build() == ["-g", "-sourcepath", "", "-proc:none", "-s", outputDir.path, USE_UNSHARED_COMPILER_TABLE_OPTION]
    }

    def "adds custom compiler args last"() {
        spec.compileOptions.compilerArgs = ["-a", "value-a", "-b", "value-b"]

        expect:
        builder.build() == defaultOptions + ["-a", "value-a", "-b", "value-b"]
    }

    def "can include/exclude main options"() {
        spec.sourceCompatibility = "1.7"

        when:
        builder.includeMainOptions(true)

        then:
        builder.build() == ["-source", "1.7"] + defaultOptions

        when:
        builder.includeMainOptions(false)

        then:
        builder.build() == []
    }

    def "includes main options by default"() {
        spec.sourceCompatibility = "1.7"

        expect:
        builder.build() == ["-source", "1.7"] + defaultOptions
    }

    def "can include/exclude classpath"() {
        def file1 = new File("/lib/lib1.jar")
        def file2 = new File("/lib/lib2.jar")
        spec.compileClasspath = [file1, file2]

        when:
        builder.includeClasspath(true)

        then:
        builder.build() == defaultOptionsWithoutClasspath + ["-classpath", "$file1$File.pathSeparator$file2"]

        when:
        builder.includeClasspath(false)

        then:
        builder.build() == defaultOptionsWithoutClasspath
    }

    def "includes classpath by default"() {
        def file1 = new File("/lib/lib1.jar")
        def file2 = new File("/lib/lib2.jar")
        spec.compileClasspath = [file1, file2]

        expect:
        builder.build() == defaultOptionsWithoutClasspath + ["-classpath", "$file1$File.pathSeparator$file2"]
    }

    def "can include/exclude launcher options"() {
        spec.compileOptions.forkOptions.with {
            memoryInitialSize = "64m"
            memoryMaximumSize = "1g"
        }

        when:
        builder.includeLauncherOptions(true)

        then:
        builder.build() == ["-J-Xms64m", "-J-Xmx1g"] + defaultOptions

        when:
        builder.includeLauncherOptions(false)

        then:
        builder.build() == defaultOptions
    }

    def "does not include launcher options by default"() {
        spec.compileOptions.forkOptions.with {
            memoryInitialSize = "64m"
            memoryMaximumSize = "1g"
        }

        expect:
        builder.build() == defaultOptions
    }

    def "can include/exclude source files"() {
        def file1 = new File("/src/Person.java")
        def file2 = new File("Computer.java")
        spec.sourceFiles = [file1, file2]

        when:
        builder.includeSourceFiles(true)

        then:
        builder.build() == defaultOptions + [file1.path, file2.path]

        when:
        builder.includeSourceFiles(false)

        then:
        builder.build() == defaultOptions
    }

    def "does not include source files by default"() {
        def file1 = new File("/src/Person.java")
        def file2 = new File("Computer.java")
        spec.sourceFiles = [file1, file2]

        expect:
        builder.build() == defaultOptions
    }

    def "generates -sourcepath option"() {
        def file1 = new File("/lib/lib1.jar")
        def file2 = new File("/lib/lib2.jar")
        def fc = [file1, file2]
        spec.compileOptions.sourcepath = fc
        def expected = ["-g", "-sourcepath", GUtil.asPath(fc), "-proc:none", USE_UNSHARED_COMPILER_TABLE_OPTION]

        expect:
        builder.build() == expected
        builder.noEmptySourcePath().build() == expected
    }

    @Unroll
    def "prohibits setting #option as compiler argument"() {
        given:
        def userProvidedPath = ['/libs/lib3.jar', '/libs/lib4.jar'].join(File.pathSeparator)
        spec.compileOptions.compilerArgs = [option, userProvidedPath]

        when:
        builder.build()

        then:
        def e = thrown(InvalidUserDataException)
        e.message.contains("Use the `$replacement` property instead.")

        where:
        option             | replacement
        '-sourcepath'      | 'CompileOptions.sourcepath'
        '--source-path'    | 'CompileOptions.sourcepath'
        '-processorpath'   | 'CompileOptions.annotationProcessorPath'
        '--processor-path' | 'CompileOptions.annotationProcessorPath'
        '-J'               | 'CompileOptions.forkOptions.jvmArgs'
        '-J-Xdiag'         | 'CompileOptions.forkOptions.jvmArgs'
    }

    def "removes sourcepath when module-source-path is provided"() {
        given:
        spec.compileOptions.sourcepath = [new File("/ignored")]
        spec.compileOptions.compilerArgs = ['--module-source-path', '/src/other']
        def expected = ["-g", "-proc:none", USE_UNSHARED_COMPILER_TABLE_OPTION, "--module-source-path", "/src/other"]

        expect:
        builder.build() == expected
        builder.noEmptySourcePath().build() == expected
    }

    String defaultEmptySourcePathRefFolder() {
        new File(spec.tempDir, JavaCompilerArgumentsBuilder.EMPTY_SOURCE_PATH_REF_DIR).absolutePath
    }

    String asPath(File... files) {
        TestFiles.fixed(files).asPath
    }
}
