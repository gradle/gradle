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
import org.gradle.api.model.ObjectFactory
import org.gradle.process.CommandLineArgumentProvider
import org.gradle.process.JavaForkOptions
import org.gradle.process.internal.DefaultJavaDebugOptions
import org.gradle.process.internal.DefaultJavaForkOptions
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.TestUtil
import org.gradle.util.UsesNativeServices
import org.junit.Rule
import spock.lang.Specification

import java.nio.charset.Charset

import static org.hamcrest.CoreMatchers.equalTo
import static org.hamcrest.CoreMatchers.nullValue
import static org.hamcrest.MatcherAssert.assertThat

@UsesNativeServices
class DefaultJavaForkOptionsTest extends Specification {
    @Rule
    public final TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider(getClass())
    private final resolver = TestFiles.pathToFileResolver(tmpDir.testDirectory)
    private final fileCollectionFactory = TestFiles.fileCollectionFactory(tmpDir.testDirectory)
    private final ObjectFactory objectFactory = TestUtil.objectFactory(tmpDir.testDirectory)
    private DefaultJavaForkOptions options

    def setup() {
        options = new DefaultJavaForkOptions(objectFactory, resolver, fileCollectionFactory)
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
        options = new DefaultJavaForkOptions(objectFactory, resolver, fileCollectionFactory)
        options.bootstrapClasspath(files[0])
        options.bootstrapClasspath(files[1])

        then:
        options.bootstrapClasspath.getFiles() == files as Set
    }

    def "allJvmArgs includes bootstrapClasspath"() {
        when:
        def files = ['file1.jar', 'file2.jar'].collect { new File(it).canonicalFile }
        options = new DefaultJavaForkOptions(objectFactory, resolver, fileCollectionFactory)
        options.bootstrapClasspath(files)

        then:
        options.allJvmArgs == ['-Xbootclasspath:' + files.join(System.properties['path.separator']), fileEncodingProperty(), *localeProperties()]
    }

    def "can set bootstrapClasspath via allJvmArgs"() {
        def files = ['file1.jar', 'file2.jar'].collect { new File(it).canonicalFile }

        when:
        options = new DefaultJavaForkOptions(objectFactory, resolver, fileCollectionFactory)
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
        1 * target.getDebugOptions() >> TestUtil.newInstance(DefaultJavaDebugOptions)

        then:
        1 * target.jvmArgs(['argFromProvider'])
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
