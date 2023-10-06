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


package org.gradle.api.internal.tasks.util

import com.google.common.collect.ImmutableSet
import org.gradle.api.file.FileCollection
import org.gradle.api.internal.file.TestFiles
import org.gradle.process.CommandLineArgumentProvider
import org.gradle.process.JavaForkOptions
import org.gradle.process.internal.DefaultJavaDebugOptions
import org.gradle.process.internal.DefaultJavaForkOptions
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.UsesNativeServices
import org.junit.Rule
import spock.lang.Specification

import java.nio.charset.Charset

import static org.gradle.api.internal.file.TestFiles.systemSpecificAbsolutePath
import static org.hamcrest.CoreMatchers.equalTo
import static org.hamcrest.CoreMatchers.nullValue
import static org.hamcrest.MatcherAssert.assertThat

@UsesNativeServices
class DefaultJavaForkOptionsTest extends Specification {
    @Rule
    public final TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider(getClass())
    private final resolver = TestFiles.pathToFileResolver(tmpDir.testDirectory)
    private final fileCollectionFactory = TestFiles.fileCollectionFactory(tmpDir.testDirectory)
    private DefaultJavaForkOptions options

    def setup() {
        options = new DefaultJavaForkOptions(resolver, fileCollectionFactory, new DefaultJavaDebugOptions())
    }

    def "provides correct default values"() {
        expect:
        options.jvmArgs.isEmpty()
        options.systemProperties.isEmpty()
        options.minHeapSize == null
        options.maxHeapSize == null
        options.bootstrapClasspath.files.isEmpty()
        !options.enableAssertions
        !options.debug
        options.allJvmArgs == [fileEncodingProperty(), *localeProperties()]
    }

    def "converts jvmArgs to String on get"() {
        when:
        options.jvmArgs = [12, "${1 + 2}"]

        then:
        options.jvmArgs == ['12', '3']
    }

    def "setAllJvmArgs cleans jvmArgumentProviders"() {
        def jvmArgumentProvider = new CommandLineArgumentProvider() {
            @Override
            Iterable<String> asArguments() {
                return ['argFromProvider']
            }
        }

        when:
        options.jvmArgumentProviders << jvmArgumentProvider
        then:
        options.allJvmArgs == ['argFromProvider', fileEncodingProperty(), *localeProperties()]

        when:
        options.allJvmArgs = ['arg1']
        then:
        options.allJvmArgs == ['arg1', fileEncodingProperty(), *localeProperties()]

        when:
        options.jvmArgumentProviders << jvmArgumentProvider
        then:
        options.allJvmArgs == ['arg1', 'argFromProvider', fileEncodingProperty(), *localeProperties()]

        when:
        options.setAllJvmArgs(ImmutableSet.of("arg2"))
        then:
        options.allJvmArgs == ['arg2', fileEncodingProperty(), *localeProperties()]
    }

    def "can add jvmArgs"() {
        when:
        options.jvmArgs('arg1', 'arg2')
        options.jvmArgs('arg3')

        then:
        options.jvmArgs == ['arg1', 'arg2', 'arg3']
    }

    def "can set system properties"() {
        when:
        options.systemProperties = [key: 12, key2: "value", key3: null]

        then:
        options.systemProperties == [key: 12, key2: "value", key3: null]
    }

    def "can add system properties"() {
        when:
        options.systemProperties(key: 12)
        options.systemProperty('key2', 'value2')

        then:
        options.systemProperties == [key: 12, key2: 'value2']
    }

    def "all jvm args include system properties as string"() {
        when:
        options.systemProperties(key: 12, key2: null, "key3": 'value')
        options.jvmArgs('arg1')

        then:
        options.allJvmArgs == ['-Dkey=12', '-Dkey2', '-Dkey3=value', 'arg1', fileEncodingProperty(), *localeProperties()]
    }

    def "system properties are updated when added using jvmArgs"() {
        when:
        options.systemProperties(key: 12)
        options.jvmArgs('-Dkey=new value', '-Dkey2')

        then:
        options.systemProperties == [key: 'new value', key2: '']

        when:
        options.allJvmArgs = []

        then:
        options.systemProperties == [:]

        when:
        options.allJvmArgs = ['-Dkey=value']

        then:
        options.systemProperties == [key: 'value']
    }

    def "allJvmArgs includes minHeapSize"() {
        when:
        options.minHeapSize = '64m'
        options.jvmArgs('arg1')

        then:
        options.allJvmArgs == ['arg1', '-Xms64m', fileEncodingProperty(), *localeProperties()]
    }

    def "allJvmArgs includes maxHeapSize"() {
        when:
        options.maxHeapSize = '1g'
        options.jvmArgs('arg1')

        then:
        options.allJvmArgs == ['arg1', '-Xmx1g', fileEncodingProperty(), *localeProperties()]
    }

    def "allJvmArgs include jvmArgumentProviders"() {
        when:
        options.jvmArgumentProviders << new CommandLineArgumentProvider() {
            @Override
            Iterable<String> asArguments() {
                return ['argFromProvider1', 'argFromProvider2']
            }
        }
        options.jvmArgumentProviders << new CommandLineArgumentProvider() {
            @Override
            Iterable<String> asArguments() {
                return ['argFromProvider3']
            }
        }
        options.jvmArgs('arg1')

        then:
        options.allJvmArgs == ['arg1', 'argFromProvider1', 'argFromProvider2', 'argFromProvider3', fileEncodingProperty(), *localeProperties()]
    }

    def "minHeapSize is updated when set using jvmArgs"() {
        when:
        options.minHeapSize = '64m'
        options.jvmArgs('-Xms128m')

        then:
        options.minHeapSize == '128m'

        when:
        options.allJvmArgs = []

        then:
        options.minHeapSize == null

        when:
        options.allJvmArgs = ['-Xms92m']

        then:
        options.minHeapSize == '92m'
    }

    def "maxHeapSizeIsUpdatedWhenSetUsingJvmArgs"() {
        options.maxHeapSize = '1g'
        options.jvmArgs('-Xmx1024m')

        assertThat(options.maxHeapSize, equalTo('1024m'))

        options.allJvmArgs = []

        assertThat(options.maxHeapSize, nullValue())

        options.allJvmArgs = ['-Xmx1g']

        assertThat(options.maxHeapSize, equalTo('1g'))
    }

    def "allJvmArgs includes assertionsEnabled"() {
        given:
        assert options.allJvmArgs == [fileEncodingProperty(), *localeProperties()]
        when:
        options.enableAssertions = true

        then:
        options.allJvmArgs == [fileEncodingProperty(), *localeProperties(), '-ea']
    }

    def "assertionsEnabled is updated when set using jvmArgs"() {
        when:
        options.jvmArgs('-ea')

        then:
        options.enableAssertions
        options.jvmArgs == []

        when:
        options.allJvmArgs = []

        then:
        !options.enableAssertions

        when:
        options.jvmArgs('-enableassertions')

        then:
        options.enableAssertions

        when:
        options.allJvmArgs = ['-da']

        then:
        !options.enableAssertions
    }

    def "allJvmArgs includes debug args"() {
        given:
        assert options.allJvmArgs == [fileEncodingProperty(), *localeProperties()]

        when:
        options.debug = true

        then:
        options.allJvmArgs == [fileEncodingProperty(), *localeProperties(), '-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=5005']
    }

    def "can set debug options"() {
        when:
        options.debugOptions {
            it.port.set(2233)
            it.host.set("*")
        }

        then:
        options.debugOptions.port.get() == 2233
        options.debugOptions.host.get() == "*"
    }

    def "allJvmArgs includes debug options port"() {
        when:
        options.debugOptions {
            it.enabled.set(true)
            it.port.set(11111)
        }

        then:
        '-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=11111' in options.allJvmArgs
    }

    def "allJvmArgs includes debug options host if it is set"() {
        when:
        options.debugOptions {
            it.enabled.set(true)
            it.port.set(22222)
            if (host != null) {
                it.host.set(host)
            }
        }

        then:
        "-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=$address".toString() in options.allJvmArgs

        where:
        host        | address
        null        | "22222"
        "127.0.0.1" | "127.0.0.1:22222"
    }

    def "can set bootstrapClasspath"() {
        def bootstrapClasspath = [:] as FileCollection

        when:
        options.bootstrapClasspath = bootstrapClasspath

        then:
        options.bootstrapClasspath.from == [bootstrapClasspath] as Set
    }

    def "can add to bootstrapClasspath"() {
        def files = ['file1.jar', 'file2.jar'].collect { new File(it).canonicalFile }

        when:
        options = new DefaultJavaForkOptions(resolver, fileCollectionFactory, new DefaultJavaDebugOptions())
        options.bootstrapClasspath(files[0])
        options.bootstrapClasspath(files[1])

        then:
        options.bootstrapClasspath.getFiles() == files as Set
    }

    def "allJvmArgs includes bootstrapClasspath"() {
        when:
        def files = ['file1.jar', 'file2.jar'].collect { new File(it).canonicalFile }
        options = new DefaultJavaForkOptions(resolver, fileCollectionFactory, new DefaultJavaDebugOptions())
        options.bootstrapClasspath(files)

        then:
        options.allJvmArgs == ['-Xbootclasspath:' + files.join(System.properties['path.separator']), fileEncodingProperty(), *localeProperties()]
    }

    def "can set bootstrapClasspath via allJvmArgs"() {
        def files = ['file1.jar', 'file2.jar'].collect { new File(it).canonicalFile }

        when:
        options = new DefaultJavaForkOptions(resolver, fileCollectionFactory, new DefaultJavaDebugOptions())
        options.bootstrapClasspath(files[0])
        options.allJvmArgs = ['-Xbootclasspath:' + files[1]]

        then:
        options.bootstrapClasspath.files == [files[1]] as Set
    }

    def "can copy to target options"() {
        JavaForkOptions target = Mock(JavaForkOptions)

        given:
        options.executable('executable')
        options.jvmArgs('arg')
        options.systemProperties(key: 12)
        options.minHeapSize = '64m'
        options.maxHeapSize = '1g'
        options.jvmArgumentProviders << new CommandLineArgumentProvider() {
            @Override
            Iterable<String> asArguments() {
                return ['argFromProvider']
            }
        }

        when:
        options.copyTo(target)

        then:
        1 * target.setExecutable('executable' as Object)
        1 * target.setJvmArgs(['arg'])
        1 * target.setSystemProperties([key: 12])
        1 * target.setMinHeapSize('64m')
        1 * target.setMaxHeapSize('1g')
        1 * target.bootstrapClasspath(_)
        1 * target.setEnableAssertions(false)
        1 * target.getDebugOptions() >> new DefaultJavaDebugOptions()

        then:
        1 * target.jvmArgs(['argFromProvider'])
    }

    def "defaults are compatible"() {
        def other = new DefaultJavaForkOptions(resolver, fileCollectionFactory, new DefaultJavaDebugOptions())

        expect:
        options.isCompatibleWith(other)
    }

    def "is compatible with identical options"() {
        def other = new DefaultJavaForkOptions(resolver, fileCollectionFactory, new DefaultJavaDebugOptions())
        def settings = {
            executable = "/foo/bar"
            workingDir = new File("foo")
            environment = ["FOO": "BAR"]
            systemProperties = ["foo": "bar", "bar": "foo"]
            minHeapSize = "128m"
            maxHeapSize = "256m"
            debug = true
        }

        when:
        options.with settings
        other.with settings

        then:
        options.isCompatibleWith(other)
    }

    def "is compatible with different representations of heap options"() {
        def other = new DefaultJavaForkOptions(resolver, fileCollectionFactory, new DefaultJavaDebugOptions())

        when:
        options.with {
            minHeapSize = "1024m"
            maxHeapSize = "2g"
        }
        other.with {
            minHeapSize = "1g"
            maxHeapSize = "2048m"
        }

        then:
        options.isCompatibleWith(other)
    }

    def "is compatible with lower heap requirements"() {
        def other = new DefaultJavaForkOptions(resolver, fileCollectionFactory, new DefaultJavaDebugOptions())

        when:
        options.with {
            minHeapSize = "128m"
            maxHeapSize = "1024m"
        }
        other.with {
            minHeapSize = "64m"
            maxHeapSize = "512m"
        }

        then:
        options.isCompatibleWith(other)
    }

    def "is not compatible with higher heap requirements"() {
        def other = new DefaultJavaForkOptions(resolver, fileCollectionFactory, new DefaultJavaDebugOptions())

        when:
        options.with {
            minHeapSize = "64m"
            maxHeapSize = "512m"
        }
        other.with {
            minHeapSize = "128m"
            maxHeapSize = "1024m"
        }

        then:
        !options.isCompatibleWith(other)
    }

    def "is compatible with the same set of jvm args"() {
        def other = new DefaultJavaForkOptions(resolver, fileCollectionFactory, new DefaultJavaDebugOptions())

        when:
        options.jvmArgs = ["-server", "-esa"]
        other.jvmArgs = ["-esa", "-server"]

        then:
        options.isCompatibleWith(other)
    }

    def "is compatible with a subset of jvm args"() {
        def other = new DefaultJavaForkOptions(resolver, fileCollectionFactory, new DefaultJavaDebugOptions())

        when:
        options.jvmArgs = ["-server", "-esa"]
        other.jvmArgs = ["-server"]

        then:
        options.isCompatibleWith(other)
    }

    def "is not compatible with a superset of jvm args"() {
        def other = new DefaultJavaForkOptions(resolver, fileCollectionFactory, new DefaultJavaDebugOptions())

        when:
        options.jvmArgs = ["-server"]
        other.jvmArgs = ["-server", "-esa"]

        then:
        !options.isCompatibleWith(other)
    }

    def "is not compatible with a different set of jvm args"() {
        def other = new DefaultJavaForkOptions(resolver, fileCollectionFactory, new DefaultJavaDebugOptions())

        when:
        options.jvmArgs = ["-server", "-esa"]
        other.jvmArgs = ["-client", "-esa"]

        then:
        !options.isCompatibleWith(other)
    }

    def "is compatible with same executable"() {
        def other = new DefaultJavaForkOptions(resolver, fileCollectionFactory, new DefaultJavaDebugOptions())

        when:
        options.executable = "foo"
        other.executable = "foo"

        then:
        options.isCompatibleWith(other)
    }

    def "is not compatible with different executables"() {
        def other = new DefaultJavaForkOptions(resolver, fileCollectionFactory, new DefaultJavaDebugOptions())

        when:
        options.executable = "foo"
        other.executable = "bar"

        then:
        !options.isCompatibleWith(other)
    }

    def "is compatible with same workingDir"() {
        def other = new DefaultJavaForkOptions(resolver, fileCollectionFactory, new DefaultJavaDebugOptions())

        when:
        options.workingDir = new File("foo")
        other.workingDir = new File("foo")

        then:
        options.isCompatibleWith(other)
    }

    def "is not compatible with different workingDir"() {
        def other = new DefaultJavaForkOptions(resolver, fileCollectionFactory, new DefaultJavaDebugOptions())

        when:
        options.workingDir = new File("foo")
        other.workingDir = new File("bar")

        then:
        !options.isCompatibleWith(other)
    }

    def "is compatible with same environment variables"() {
        def other = new DefaultJavaForkOptions(resolver, fileCollectionFactory, new DefaultJavaDebugOptions())

        when:
        options.environment = ["FOO": "bar", "BAR": "foo"]
        other.environment = ["BAR": "foo", "FOO": "bar"]

        then:
        options.isCompatibleWith(other)
    }

    def "is not compatible with different environment variables"() {
        def other = new DefaultJavaForkOptions(resolver, fileCollectionFactory, new DefaultJavaDebugOptions())

        when:
        options.environment = ["FOO": "bar", "BAR": "foo"]
        other.environment = ["BAZ": "foo", "FOO": "bar"]

        then:
        !options.isCompatibleWith(other)
    }

    def "is compatible with subset of environment variables"() {
        def other = new DefaultJavaForkOptions(resolver, fileCollectionFactory, new DefaultJavaDebugOptions())

        when:
        options.environment = ["FOO": "bar", "BAR": "foo"]
        other.environment = ["FOO": "bar"]

        then:
        options.isCompatibleWith(other)
    }

    def "is not compatible with super set of environment variables"() {
        def other = new DefaultJavaForkOptions(resolver, fileCollectionFactory, new DefaultJavaDebugOptions())

        when:
        options.environment = ["FOO": "bar"]
        other.environment = ["FOO": "bar", "BAR": "foo"]

        then:
        !options.isCompatibleWith(other)
    }

    def "is compatible with the same system properties"() {
        def other = new DefaultJavaForkOptions(resolver, fileCollectionFactory, new DefaultJavaDebugOptions())

        when:
        options.systemProperties = ["foo": "bar", "bar": "foo"]
        other.systemProperties = ["bar": "foo", "foo": "bar"]

        then:
        options.isCompatibleWith(other)
    }

    def "is not compatible with different system properties"() {
        def other = new DefaultJavaForkOptions(resolver, fileCollectionFactory, new DefaultJavaDebugOptions())

        when:
        options.systemProperties = ["foo": "bar", "bar": "foo"]
        other.systemProperties = ["baz": "foo", "foo": "bar"]

        then:
        !options.isCompatibleWith(other)
    }

    def "is compatible with subset of system properties"() {
        def other = new DefaultJavaForkOptions(resolver, fileCollectionFactory, new DefaultJavaDebugOptions())

        when:
        options.systemProperties = ["foo": "bar", "bar": "foo"]
        other.systemProperties = ["foo": "bar"]

        then:
        options.isCompatibleWith(other)
    }

    def "is not compatible with super set of system properties"() {
        def other = new DefaultJavaForkOptions(resolver, fileCollectionFactory, new DefaultJavaDebugOptions())

        when:
        options.systemProperties = ["foo": "bar"]
        other.systemProperties = ["foo": "bar", "bar": "foo"]

        then:
        !options.isCompatibleWith(other)
    }

    def "is compatible with same debug setting"() {
        def other = new DefaultJavaForkOptions(resolver, fileCollectionFactory, new DefaultJavaDebugOptions())

        when:
        options.debug = true
        other.debug = true

        then:
        options.isCompatibleWith(other)
    }

    def "is not compatible with different debug setting"() {
        def other = new DefaultJavaForkOptions(resolver, fileCollectionFactory, new DefaultJavaDebugOptions())

        when:
        options.debug = true
        other.debug = false

        then:
        !options.isCompatibleWith(other)
    }

    def "is compatible with same enableAssertions setting"() {
        def other = new DefaultJavaForkOptions(resolver, fileCollectionFactory, new DefaultJavaDebugOptions())

        when:
        options.enableAssertions = true
        other.enableAssertions = true

        then:
        options.isCompatibleWith(other)
    }

    def "is not compatible with different enableAssertions setting"() {
        def other = new DefaultJavaForkOptions(resolver, fileCollectionFactory, new DefaultJavaDebugOptions())

        when:
        options.enableAssertions = true
        other.enableAssertions = false

        then:
        !options.isCompatibleWith(other)
    }

    def "is compatible with same bootstrapClasspath"() {
        def files = ['file1.jar', 'file2.jar'].collect { new File(it).canonicalFile }
        def options = new DefaultJavaForkOptions(resolver, fileCollectionFactory, new DefaultJavaDebugOptions())
        def other = new DefaultJavaForkOptions(resolver, fileCollectionFactory, new DefaultJavaDebugOptions())

        when:
        options.with {
            workingDir = systemSpecificAbsolutePath("foo")
            bootstrapClasspath(files)
        }
        other.with {
            workingDir = systemSpecificAbsolutePath("foo")
            bootstrapClasspath(files)
        }

        then:
        options.isCompatibleWith(other)
    }

    def "is not compatible with different bootstrapClasspath"() {
        def files1 = ['file1.jar', 'file2.jar'].collect { new File(it).canonicalFile }
        def files2 = ['file2.jar', 'file3.jar'].collect { new File(it).canonicalFile }
        def options = new DefaultJavaForkOptions(resolver, fileCollectionFactory, new DefaultJavaDebugOptions())
        def other = new DefaultJavaForkOptions(resolver, fileCollectionFactory, new DefaultJavaDebugOptions())

        when:
        options.with {
            workingDir = systemSpecificAbsolutePath("foo")
            bootstrapClasspath(files1)
        }
        other.with {
            workingDir = systemSpecificAbsolutePath("foo")
            bootstrapClasspath(files2)
        }

        then:
        !options.isCompatibleWith(other)
    }

    def "string values are trimmed"() {
        def other = new DefaultJavaForkOptions(resolver, fileCollectionFactory, new DefaultJavaDebugOptions())

        when:
        options.with {
            executable = " foo"
            minHeapSize = "128m "
            maxHeapSize = "1g"
            jvmArgs = [" -server", "-esa"]
        }
        other.with {
            executable = "foo "
            minHeapSize = "128m"
            maxHeapSize = " 1g"
            jvmArgs = [" -server", "-esa "]
        }

        then:
        options.isCompatibleWith(other)
    }

    def "capitalization of memory options is irrelevant"() {
        def other = new DefaultJavaForkOptions(resolver, fileCollectionFactory, new DefaultJavaDebugOptions())

        when:
        options.with {
            minHeapSize = "128M"
            maxHeapSize = "1g"
        }
        other.with {
            minHeapSize = "128m"
            maxHeapSize = "1G"
        }

        then:
        options.isCompatibleWith(other)
    }

    def "capitalization of JVM args is relevant"() {
        def other = new DefaultJavaForkOptions(resolver, fileCollectionFactory, new DefaultJavaDebugOptions())

        when:
        options.with {
            jvmArgs = ["-Server", "-esa"]
        }
        other.with {
            jvmArgs = ["-server", "-esa"]
        }

        then:
        !options.isCompatibleWith(other)
    }

    def "unspecified heap options are only compatible with unspecified heap options"() {
        def other1 = new DefaultJavaForkOptions(resolver, fileCollectionFactory, new DefaultJavaDebugOptions())
        def other2 = new DefaultJavaForkOptions(resolver, fileCollectionFactory, new DefaultJavaDebugOptions())

        when:
        other2.with {
            minHeapSize = "128m"
            maxHeapSize = "256m"
        }

        then:
        options.isCompatibleWith(other1)
        !options.isCompatibleWith(other2)
    }

    def "unspecified executable is only compatible with unspecified executable options"() {
        def other1 = new DefaultJavaForkOptions(resolver, fileCollectionFactory, new DefaultJavaDebugOptions())
        def other2 = new DefaultJavaForkOptions(resolver, fileCollectionFactory, new DefaultJavaDebugOptions())

        when:
        other2.executable = "/foo/bar"

        then:
        options.isCompatibleWith(other1)
        !options.isCompatibleWith(other2)
    }

    def "cannot determine compatibility with jvmArgumentProviders"() {
        def other = new DefaultJavaForkOptions(resolver, fileCollectionFactory, new DefaultJavaDebugOptions())
        def argumentProvider = new CommandLineArgumentProvider() {
            @Override
            Iterable<String> asArguments() {
                return ['argFromProvider']
            }
        }
        if (currentHasProviders) {
            options.jvmArgumentProviders << argumentProvider
        }
        if (otherHasProviders) {
            other.jvmArgumentProviders << argumentProvider
        }

        when:
        options.isCompatibleWith(other)

        then:
        def thrown = thrown(UnsupportedOperationException)
        thrown.message.contains('Cannot compare options with jvmArgumentProviders.')

        where:
        currentHasProviders | otherHasProviders
        true                | false
        false               | true
        true                | true

    }

    private static String fileEncodingProperty(String encoding = Charset.defaultCharset().name()) {
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
