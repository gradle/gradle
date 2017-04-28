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

import java.nio.charset.Charset
import org.gradle.api.file.FileCollection
import org.gradle.api.internal.file.FileResolver
import org.gradle.process.JavaForkOptions
import org.gradle.process.internal.DefaultJavaForkOptions
import org.gradle.util.JUnit4GroovyMockery
import org.gradle.internal.jvm.Jvm
import org.jmock.integration.junit4.JMock
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import static org.gradle.util.Matchers.isEmpty
import static org.gradle.util.Matchers.isEmptyMap
import static org.hamcrest.Matchers.*
import static org.junit.Assert.*

@RunWith(JMock.class)
public class DefaultJavaForkOptionsTest {
    private final JUnit4GroovyMockery context = new JUnit4GroovyMockery()
    private final FileResolver resolver = context.mock(FileResolver.class)
    private DefaultJavaForkOptions options

    @Before
    public void setup() {
        context.checking {
            allowing(resolver).resolveLater(".")
        }
        options = new DefaultJavaForkOptions(resolver, Jvm.current())
    }

    @Test
    public void defaultValues() {
        assertThat(options.executable, notNullValue())
        assertThat(options.jvmArgs, isEmpty())
        assertThat(options.systemProperties, isEmptyMap())
        assertThat(options.minHeapSize, nullValue())
        assertThat(options.maxHeapSize, nullValue())
        assertThat(options.bootstrapClasspath.files, isEmpty())
        assertFalse(options.enableAssertions)
        assertFalse(options.debug)
        assert options.allJvmArgs == [fileEncodingProperty(), *localeProperties()]
    }

    @Test
    public void convertsJvmArgsToStringOnGet() {
        options.jvmArgs = [12, "${1 + 2}"]
        assertThat(options.jvmArgs, equalTo(['12', '3']))
    }

    @Test
    public void canAddJvmArgs() {
        options.jvmArgs('arg1', 'arg2')
        assertThat(options.jvmArgs, equalTo(['arg1', 'arg2']))
    }

    @Test
    public void canSetSystemProperties() {
        options.systemProperties = [key: 12, key2: "value", key3: null]
        assertThat(options.systemProperties, equalTo(key: 12, key2: "value", key3: null))
    }

    @Test
    public void canAddSystemProperties() {
        options.systemProperties(key: 12)
        options.systemProperty('key2', 'value2')
        assertThat(options.systemProperties, equalTo(key: 12, key2: 'value2'))
    }

    @Test
    public void allJvmArgsIncludeSystemPropertiesAsString() {
        options.systemProperties(key: 12, key2: null, "key3": 'value')
        options.jvmArgs('arg1')

        assert options.allJvmArgs == ['-Dkey=12', '-Dkey2', '-Dkey3=value', 'arg1', fileEncodingProperty(), *localeProperties()]
    }

    @Test
    public void systemPropertiesAreUpdatedWhenAddedUsingJvmArgs() {
        options.systemProperties(key: 12)
        options.jvmArgs('-Dkey=new value', '-Dkey2')

        assertThat(options.systemProperties, equalTo(key: 'new value', key2: ''))

        options.allJvmArgs = []

        assertThat(options.systemProperties, equalTo([:]))

        options.allJvmArgs = ['-Dkey=value']

        assertThat(options.systemProperties, equalTo([key: 'value']))
    }

    @Test
    public void allJvmArgsIncludeMinHeapSize() {
        options.minHeapSize = '64m'
        options.jvmArgs('arg1')
        assert options.allJvmArgs == ['arg1', '-Xms64m', fileEncodingProperty(), *localeProperties()]
    }

    @Test
    public void allJvmArgsIncludeMaxHeapSize() {
        options.maxHeapSize = '1g'
        options.jvmArgs('arg1')
        assert options.allJvmArgs == ['arg1', '-Xmx1g', fileEncodingProperty(), *localeProperties()]
    }

    @Test
    public void minHeapSizeIsUpdatedWhenSetUsingJvmArgs() {
        options.minHeapSize = '64m'
        options.jvmArgs('-Xms128m')

        assertThat(options.minHeapSize, equalTo('128m'))

        options.allJvmArgs = []

        assertThat(options.minHeapSize, nullValue())

        options.allJvmArgs = ['-Xms92m']

        assertThat(options.minHeapSize, equalTo('92m'))
    }

    @Test
    public void maxHeapSizeIsUpdatedWhenSetUsingJvmArgs() {
        options.maxHeapSize = '1g'
        options.jvmArgs('-Xmx1024m')

        assertThat(options.maxHeapSize, equalTo('1024m'))

        options.allJvmArgs = []

        assertThat(options.maxHeapSize, nullValue())

        options.allJvmArgs = ['-Xmx1g']

        assertThat(options.maxHeapSize, equalTo('1g'))
    }

    @Test
    public void allJvmArgsIncludeAssertionsEnabled() {
        assert options.allJvmArgs == [fileEncodingProperty(), *localeProperties()]
        options.enableAssertions = true
        assert options.allJvmArgs == [fileEncodingProperty(), *localeProperties(), '-ea']
    }

    @Test
    public void assertionsEnabledIsUpdatedWhenSetUsingJvmArgs() {
        options.jvmArgs('-ea')
        assertTrue(options.enableAssertions)
        assertThat(options.jvmArgs, equalTo([]))

        options.allJvmArgs = []
        assertFalse(options.enableAssertions)

        options.jvmArgs('-enableassertions')
        assertTrue(options.enableAssertions)

        options.allJvmArgs = ['-da']
        assertFalse(options.enableAssertions)
    }

    @Test
    public void allJvmArgsIncludeDebugArgs() {
        assert options.allJvmArgs == [fileEncodingProperty(), *localeProperties()]
        options.debug = true
        assert options.allJvmArgs  == [fileEncodingProperty(), *localeProperties(), '-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=5005']
    }

    @Test
    public void debugIsEnabledWhenSetUsingJvmArgs() {
        options.jvmArgs('-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=5005')
        assertTrue(options.debug)
        assertThat(options.jvmArgs, equalTo([]))

        options.allJvmArgs = []
        assertFalse(options.debug)

        options.debug = false
        options.jvmArgs = ['-Xdebug']
        assertFalse(options.debug)
        assertThat(options.jvmArgs, equalTo(['-Xdebug']))

        options.jvmArgs = ['-Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=5005']
        assertFalse(options.debug)
        assertThat(options.jvmArgs, equalTo(['-Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=5005']))

        options.jvmArgs '-Xdebug'
        assertTrue(options.debug)
        assertThat(options.jvmArgs, equalTo([]))

        options.debug = false
        options.jvmArgs = ['-Xdebug', '-Xrunjdwp:transport=other']
        assertFalse(options.debug)
        assertThat(options.jvmArgs, equalTo(['-Xdebug', '-Xrunjdwp:transport=other']))

        options.debug = false
        options.allJvmArgs = ['-Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=5005', '-Xdebug']
        assertTrue(options.debug)
        assertThat(options.jvmArgs, equalTo([]))

        options.debug = false
        options.allJvmArgs = ['-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=5005']
        assertTrue(options.debug)
        assertThat(options.jvmArgs, equalTo([]))
    }

    @Test
    public void canSetBootstrapClasspath() {
        def bootstrapClasspath = [:] as FileCollection
        options.bootstrapClasspath = bootstrapClasspath

        assertThat(options.bootstrapClasspath.from, equalTo([bootstrapClasspath] as Set))
    }

    @Test
    public void canAddToBootstrapClasspath() {
        def files = ['file1.jar', 'file2.jar'].collect { new File(it).canonicalFile }
        options = new DefaultJavaForkOptions(TestFiles.resolver());
        options.bootstrapClasspath(files[0])
        options.bootstrapClasspath(files[1])

        assertThat(options.bootstrapClasspath.getFiles(), equalTo(files as Set))
    }

    @Test
    public void allJvmArgsIncludeBootstrapClasspath() {
        def files = ['file1.jar', 'file2.jar'].collect { new File(it).canonicalFile }
        options = new DefaultJavaForkOptions(TestFiles.resolver());
        options.bootstrapClasspath(files)

        context.checking {
            allowing(resolver).resolveFiles(['file.jar'])
            will(returnValue([isEmpty: {false}, getAsPath: {'<classpath>'}] as FileCollection))
        }

        assert options.allJvmArgs  == ['-Xbootclasspath:' + files.join(System.properties['path.separator']), fileEncodingProperty(), *localeProperties()]
    }

    @Test
    public void canSetBootstrapClasspathViaAllJvmArgs() {
        def files = ['file1.jar', 'file2.jar'].collect { new File(it).canonicalFile }
        options = new DefaultJavaForkOptions(TestFiles.resolver());
        options.bootstrapClasspath(files[0])

        options.allJvmArgs = ['-Xbootclasspath:' + files[1]]

        assertThat(options.bootstrapClasspath.files, equalTo([files[1]] as Set))
    }

    @Test
    public void canCopyToTargetOptions() {
        options.executable('executable')
        options.jvmArgs('arg')
        options.systemProperties(key: 12)
        options.minHeapSize = '64m'
        options.maxHeapSize = '1g'

        JavaForkOptions target = context.mock(JavaForkOptions.class)
        context.checking {
            one(target).setExecutable('executable' as Object)
            one(target).setJvmArgs(['arg'] as Iterable<?>)
            one(target).setSystemProperties(key: 12)
            one(target).setMinHeapSize('64m')
            one(target).setMaxHeapSize('1g')
            one(target).setBootstrapClasspath(options.bootstrapClasspath)
            one(target).setEnableAssertions(false)
            one(target).setDebug(false)
            ignoring(target)
        }

        options.copyTo(target)
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


