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

import org.gradle.api.JavaVersion
import org.gradle.api.internal.file.collections.SimpleFileCollection
import org.gradle.api.tasks.compile.CompileOptions
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification

import static org.gradle.api.internal.tasks.compile.JavaCompilerArgumentsBuilder.USE_UNSHARED_COMPILER_TABLE_OPTION

class JavaCompilerArgumentsBuilderTest extends Specification {

    @Rule
    TestNameTestDirectoryProvider tempDir = new TestNameTestDirectoryProvider()

    def spec = new DefaultJavaCompileSpec()
    def builder = new JavaCompilerArgumentsBuilder(spec)

    def setup() {
        spec.tempDir = tempDir.file("tmp")
        spec.compileOptions = new CompileOptions()
    }

    def "generates options for an unconfigured spec"() {
        expect:
        builder.build() == ["-g", USE_UNSHARED_COMPILER_TABLE_OPTION]
    }

    def "generates no -source option when current Jvm Version is used"() {
        spec.sourceCompatibility = JavaVersion.current().toString();

        expect:
        builder.build() == ["-g", USE_UNSHARED_COMPILER_TABLE_OPTION]
    }

    def "generates -source option when compatibility differs from current Jvm version"() {
        spec.sourceCompatibility = "1.4"

        expect:
        builder.build() == ["-source", "1.4", "-g", USE_UNSHARED_COMPILER_TABLE_OPTION]
    }

    def "generates no -target option when current Jvm Version is used"() {
        spec.targetCompatibility = JavaVersion.current().toString();

        expect:
        builder.build() == ["-g", USE_UNSHARED_COMPILER_TABLE_OPTION]
    }

    def "generates -target option when compatibility differs current Jvm version"() {
        spec.targetCompatibility = "1.4"

        expect:
        builder.build() == ["-target", "1.4", "-g", USE_UNSHARED_COMPILER_TABLE_OPTION]
    }

    def "generates -d option"() {
        def file = new File("/project/build")
        spec.destinationDir = file

        expect:
        builder.build() == ["-d", file.path, "-g", USE_UNSHARED_COMPILER_TABLE_OPTION]
    }

    def "generates -verbose option"() {
        when:
        spec.compileOptions.verbose = true

        then:
        builder.build() == ["-verbose", "-g", USE_UNSHARED_COMPILER_TABLE_OPTION]

        when:
        spec.compileOptions.verbose = false

        then:
        builder.build() == ["-g", USE_UNSHARED_COMPILER_TABLE_OPTION]
    }

    def "generates -deprecation option"() {
        when:
        spec.compileOptions.deprecation = true

        then:
        builder.build() == ["-deprecation", "-g", USE_UNSHARED_COMPILER_TABLE_OPTION]

        when:
        spec.compileOptions.deprecation = false

        then:
        builder.build() == ["-g", USE_UNSHARED_COMPILER_TABLE_OPTION]
    }

    def "generates -nowarn option"() {
        when:
        spec.compileOptions.warnings = true

        then:
        builder.build() == ["-g", USE_UNSHARED_COMPILER_TABLE_OPTION]

        when:
        spec.compileOptions.warnings = false

        then:
        builder.build() == ["-nowarn", "-g", USE_UNSHARED_COMPILER_TABLE_OPTION]
    }

    def "generates -g option"() {
        when:
        spec.compileOptions.debug = true

        then:
        builder.build() == ["-g", USE_UNSHARED_COMPILER_TABLE_OPTION]

        when:
        spec.compileOptions.debugOptions.debugLevel = "source,vars"

        then:
        builder.build() == ["-g:source,vars", USE_UNSHARED_COMPILER_TABLE_OPTION]

        when:
        spec.compileOptions.debug = false

        then:
        builder.build() == ["-g:none", USE_UNSHARED_COMPILER_TABLE_OPTION]
    }

    def "generates -encoding option"() {
        spec.compileOptions.encoding = "some-encoding"

        expect:
        builder.build() == ["-g", "-encoding", "some-encoding", USE_UNSHARED_COMPILER_TABLE_OPTION]
    }

    def "generates -bootclasspath option"() {
        spec.compileOptions.bootClasspath = "/lib/lib1.jar:/lib/lib2.jar"

        expect:
        builder.build() == ["-g", "-bootclasspath", "/lib/lib1.jar:/lib/lib2.jar", USE_UNSHARED_COMPILER_TABLE_OPTION]
    }

    def "generates -extdirs option"() {
        spec.compileOptions.extensionDirs = "/dir1:/dir2"

        expect:
        builder.build() == ["-g", "-extdirs", "/dir1:/dir2", USE_UNSHARED_COMPILER_TABLE_OPTION]
    }

    def "generates -classpath option"() {
        def file1 = new File("/lib/lib1.jar")
        def file2 = new File("/lib/lib2.jar")
        spec.classpath = [file1, file2]

        expect:
        builder.build() == ["-g", "-sourcepath", defaultEmptySourcePathRefFolder(), "-classpath", "$file1$File.pathSeparator$file2", USE_UNSHARED_COMPILER_TABLE_OPTION]
    }

    def "adds custom compiler args"() {
        spec.compileOptions.compilerArgs = ["-a", "value-a", "-b", "value-b"]

        expect:
        builder.build() == ["-g", "-a", "value-a", "-b", "value-b", USE_UNSHARED_COMPILER_TABLE_OPTION]
    }

    def "can include/exclude main options"() {
        spec.sourceCompatibility = "1.4"

        when:
        builder.includeMainOptions(true)

        then:
        builder.build() == ["-source", "1.4", "-g", USE_UNSHARED_COMPILER_TABLE_OPTION]

        when:
        builder.includeMainOptions(false)

        then:
        builder.build() == [USE_UNSHARED_COMPILER_TABLE_OPTION]
    }

    def "includes main options by default"() {
        spec.sourceCompatibility = "1.4"

        expect:
        builder.build() == ["-source", "1.4", "-g", USE_UNSHARED_COMPILER_TABLE_OPTION]
    }

    def "can include/exclude classpath"() {
        def file1 = new File("/lib/lib1.jar")
        def file2 = new File("/lib/lib2.jar")
        spec.classpath = [file1, file2]

        when:
        builder.includeClasspath(true)

        then:
        builder.build() == ["-g", "-sourcepath", defaultEmptySourcePathRefFolder(), "-classpath", "$file1$File.pathSeparator$file2", USE_UNSHARED_COMPILER_TABLE_OPTION]

        when:
        builder.includeClasspath(false)

        then:
        builder.build() == ["-g", USE_UNSHARED_COMPILER_TABLE_OPTION]
    }

    def "includes classpath by default"() {
        def file1 = new File("/lib/lib1.jar")
        def file2 = new File("/lib/lib2.jar")
        spec.classpath = [file1, file2]

        expect:
        builder.build() == ["-g", "-sourcepath", defaultEmptySourcePathRefFolder(), "-classpath", "$file1$File.pathSeparator$file2", USE_UNSHARED_COMPILER_TABLE_OPTION]
    }

    def "can include/exclude launcher options"() {
        spec.compileOptions.forkOptions.with {
            memoryInitialSize = "64m"
            memoryMaximumSize = "1g"
        }

        when:
        builder.includeLauncherOptions(true)

        then:
        builder.build() == ["-J-Xms64m", "-J-Xmx1g", "-g", USE_UNSHARED_COMPILER_TABLE_OPTION]

        when:
        builder.includeLauncherOptions(false)

        then:
        builder.build() == ["-g", USE_UNSHARED_COMPILER_TABLE_OPTION]
    }

    def "does not include launcher options by default"() {
        spec.compileOptions.forkOptions.with {
            memoryInitialSize = "64m"
            memoryMaximumSize = "1g"
        }

        expect:
        builder.build() == ["-g", USE_UNSHARED_COMPILER_TABLE_OPTION]
    }

    def "can include/exclude source files"() {
        def file1 = new File("/src/Person.java")
        def file2 = new File("Computer.java")
        spec.source = new SimpleFileCollection(file1, file2)

        when:
        builder.includeSourceFiles(true)

        then:
        builder.build() == ["-g", file1.path, file2.path, USE_UNSHARED_COMPILER_TABLE_OPTION]

        when:
        builder.includeSourceFiles(false)

        then:
        builder.build() == ["-g", USE_UNSHARED_COMPILER_TABLE_OPTION]
    }

    def "does not include source files by default"() {
        def file1 = new File("/src/Person.java")
        def file2 = new File("Computer.java")
        spec.source = new SimpleFileCollection(file1, file2)

        expect:
        builder.build() == ["-g", USE_UNSHARED_COMPILER_TABLE_OPTION]
    }

    def "generates -sourcepath option"() {
        def file1 = new File("/lib/lib1.jar")
        def file2 = new File("/lib/lib2.jar")
        def fc = new SimpleFileCollection(file1, file2)
        spec.compileOptions.sourcepath = fc

        expect:
        builder.build() == ["-g", "-sourcepath", fc.asPath, USE_UNSHARED_COMPILER_TABLE_OPTION]
    }

    def "generates -sourcepath option when classpath not included and explicit value for source path"() {
        def file1 = new File("/lib/lib1.jar")
        def file2 = new File("/lib/lib2.jar")
        spec.compileOptions.sourcepath = new SimpleFileCollection(file1)
        spec.classpath = new SimpleFileCollection(file2)

        expect:
        builder.build() == ["-g", "-sourcepath", "$file1", "-classpath", asPath(file2), USE_UNSHARED_COMPILER_TABLE_OPTION]
        builder.includeClasspath(false).build() == ["-g", "-sourcepath", asPath(file1), USE_UNSHARED_COMPILER_TABLE_OPTION]

        when:
        spec.compileOptions.sourcepath = null

        then:
        builder.includeClasspath(true).build() == ["-g", "-sourcepath", defaultEmptySourcePathRefFolder(), "-classpath", asPath(file2), USE_UNSHARED_COMPILER_TABLE_OPTION]
        builder.includeClasspath(false).build() == ["-g", USE_UNSHARED_COMPILER_TABLE_OPTION]

        when:
        spec.classpath = null

        then:
        builder.includeClasspath(true).build() == ["-g", USE_UNSHARED_COMPILER_TABLE_OPTION]
        builder.includeClasspath(false).build() == ["-g", USE_UNSHARED_COMPILER_TABLE_OPTION]
    }

    String defaultEmptySourcePathRefFolder() {
        new File(spec.tempDir, JavaCompilerArgumentsBuilder.EMPTY_SOURCE_PATH_REF_DIR).absolutePath
    }

    def "can exclude customizations"() {
        expect:
        builder.includeCustomizations(false).build() == ["-g"]
    }

    String asPath(File... files) {
        new SimpleFileCollection(files).asPath
    }
}
