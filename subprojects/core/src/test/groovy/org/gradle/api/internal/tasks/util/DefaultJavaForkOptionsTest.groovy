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

@UsesNativeServices
class DefaultJavaForkOptionsTest extends Specification {
    @Rule
    public final TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider(getClass())
    private final resolver = TestFiles.pathToFileResolver(tmpDir.testDirectory)
    private final fileCollectionFactory = TestFiles.fileCollectionFactory(tmpDir.testDirectory)
    private final ObjectFactory objectFactory = TestUtil.objectFactory(tmpDir.testDirectory)
    private DefaultJavaForkOptions options

    def setup() {
        options = TestUtil.newInstance(DefaultJavaForkOptions, objectFactory, resolver, fileCollectionFactory)
    }

    def "provides correct default values"() {
        expect:
        options.jvmArgs.get().isEmpty()
        options.systemProperties.get().isEmpty()
        options.minHeapSize.getOrNull() == null
        options.maxHeapSize.getOrNull() == null
        options.bootstrapClasspath.files.isEmpty()
        options.enableAssertions.getOrNull() == null
        !options.debug.get()
        options.allJvmArgs.get() == [fileEncodingProperty(), *localeProperties()]
    }

    def "converts jvmArgs from GString to String on get"() {
        when:
        options.jvmArgs = ["12", "${1 + 2}"]

        then:
        options.jvmArgs.get() == ['12', '3']
    }

    def "can add jvmArgs"() {
        when:
        options.jvmArgs('arg1', 'arg2')
        options.jvmArgs('arg3')

        then:
        options.jvmArgs.get() == ['arg1', 'arg2', 'arg3']
    }

    def "can set system properties"() {
        when:
        options.systemProperties = [key: 12, key2: "value"]

        then:
        options.systemProperties.get() == [key: 12, key2: "value"]
    }

    def "can add system properties"() {
        when:
        options.systemProperties(key: 12)
        options.systemProperty('key2', 'value2')

        then:
        options.systemProperties.get() == [key: 12, key2: 'value2']
    }

    def "all jvm args include system properties as string"() {
        when:
        options.systemProperties(key: 12, key2: null, "key3": 'value')
        options.jvmArgs('arg1')

        then:
        options.allJvmArgs.get() == ['-Dkey=12', '-Dkey2', '-Dkey3=value', 'arg1', fileEncodingProperty(), *localeProperties()]
    }

    def "allJvmArgs includes minHeapSize"() {
        when:
        options.minHeapSize = '64m'
        options.jvmArgs('arg1')

        then:
        options.allJvmArgs.get() == ['arg1', '-Xms64m', fileEncodingProperty(), *localeProperties()]
    }

    def "allJvmArgs includes maxHeapSize"() {
        when:
        options.maxHeapSize = '1g'
        options.jvmArgs('arg1')

        then:
        options.allJvmArgs.get() == ['arg1', '-Xmx1g', fileEncodingProperty(), *localeProperties()]
    }

    def "allJvmArgs include jvmArgumentProviders"() {
        when:
        options.jvmArgumentProviders.add(new CommandLineArgumentProvider() {
            @Override
            Iterable<String> asArguments() {
                return ['argFromProvider1', 'argFromProvider2']
            }
        })
        options.jvmArgumentProviders.add(new CommandLineArgumentProvider() {
            @Override
            Iterable<String> asArguments() {
                return ['argFromProvider3']
            }
        })
        options.jvmArgs('arg1')

        then:
        options.allJvmArgs.get() == ['arg1', 'argFromProvider1', 'argFromProvider2', 'argFromProvider3', fileEncodingProperty(), *localeProperties()]
    }

    def "allJvmArgs includes assertionsEnabled"() {
        given:
        assert options.allJvmArgs.get() == [fileEncodingProperty(), *localeProperties()]
        when:
        options.enableAssertions = true

        then:
        options.allJvmArgs.get() == [fileEncodingProperty(), *localeProperties(), '-ea']
    }

    def "allJvmArgs includes debug args"() {
        given:
        assert options.allJvmArgs.get() == [fileEncodingProperty(), *localeProperties()]

        when:
        options.debug = true

        then:
        options.allJvmArgs.get() == [fileEncodingProperty(), *localeProperties(), '-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=5005']
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
        '-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=11111' in options.allJvmArgs.get()
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
        "-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=$address".toString() in options.allJvmArgs.get()

        where:
        host        | address
        null        | "22222"
        "127.0.0.1" | "127.0.0.1:22222"
    }

    def "can set bootstrapClasspath"() {
        def bootstrapClasspath = TestUtil.objectFactory().fileCollection()

        when:
        options.bootstrapClasspath = bootstrapClasspath

        then:
        options.bootstrapClasspath.from == [bootstrapClasspath] as Set
    }

    def "can add to bootstrapClasspath"() {
        def files = ['file1.jar', 'file2.jar'].collect { new File(it).canonicalFile }

        when:
        options = TestUtil.newInstance(DefaultJavaForkOptions, objectFactory, resolver, fileCollectionFactory)
        options.bootstrapClasspath(files[0])
        options.bootstrapClasspath(files[1])

        then:
        options.bootstrapClasspath.getFiles() == files as Set
    }

    def "allJvmArgs includes bootstrapClasspath"() {
        when:
        def files = ['file1.jar', 'file2.jar'].collect { new File(it).canonicalFile }
        options = TestUtil.newInstance(DefaultJavaForkOptions, objectFactory, resolver, fileCollectionFactory)
        options.bootstrapClasspath(files)

        then:
        options.allJvmArgs.get() == ['-Xbootclasspath:' + files.join(System.properties['path.separator']), fileEncodingProperty(), *localeProperties()]
    }

    def "can copy to target options"() {
        JavaForkOptions target = TestUtil.newInstance(DefaultJavaForkOptions, objectFactory, resolver, fileCollectionFactory)

        given:
        options.executable('executable')
        options.jvmArgs('arg')
        options.systemProperties(key: 12)
        options.bootstrapClasspath(new File('file1.jar').canonicalFile)
        options.enableAssertions = true
        options.minHeapSize = '64m'
        options.maxHeapSize = '1g'
        options.jvmArgumentProviders.add(new CommandLineArgumentProvider() {
            @Override
            Iterable<String> asArguments() {
                return ['argFromProvider']
            }
        })

        when:
        options.copyTo(target)

        then:
        target.getExecutable() == 'executable'
        target.getJvmArgs().get() == ['arg']
        target.getSystemProperties().get() == [key: 12]
        target.getMinHeapSize().get() == '64m'
        target.getMaxHeapSize().get() == '1g'
        target.getBootstrapClasspath().files == [new File('file1.jar').canonicalFile] as Set
        target.getEnableAssertions().get() == true
        target.getDebugOptions() >> new DefaultJavaDebugOptions()
        target.getJvmArgumentProviders().get()[0].asArguments() == ['argFromProvider']
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
