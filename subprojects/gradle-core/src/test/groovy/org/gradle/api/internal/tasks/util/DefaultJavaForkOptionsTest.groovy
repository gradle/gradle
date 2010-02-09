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


import static org.junit.Assert.*
import static org.hamcrest.Matchers.*
import static org.gradle.util.Matchers.*
import org.gradle.util.JUnit4GroovyMockery
import org.jmock.integration.junit4.JMock
import org.junit.runner.RunWith
import org.gradle.api.internal.file.FileResolver
import org.junit.Test
import org.gradle.util.Jvm
import org.gradle.api.tasks.util.JavaForkOptions

@RunWith(JMock.class)
public class DefaultJavaForkOptionsTest {
    private final JUnit4GroovyMockery context = new JUnit4GroovyMockery()
    private final DefaultJavaForkOptions options = new DefaultJavaForkOptions(context.mock(FileResolver.class), Jvm.current())

    @Test
    public void defaultValues() {
        assertThat(options.executable, notNullValue())
        assertThat(options.jvmArgs, isEmpty())
        assertThat(options.systemProperties, isEmptyMap())
        assertThat(options.maxHeapSize, nullValue())
        assertThat(options.allJvmArgs, isEmpty())
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
    public void allJvmArgsIncludeSystemProperties() {
        options.systemProperties(key: 12, key2: null)
        options.jvmArgs('arg1')

        assertThat(options.allJvmArgs, equalTo(['arg1', '-Dkey=12', '-Dkey2']))
    }

    @Test
    public void systemPropertiesAreUpdatedWhenAddedUsingJvmArgs() {
        options.systemProperties(key: 12)
        options.jvmArgs('-Dkey=new value', '-Dkey2')

        assertThat(options.systemProperties, equalTo(key: 'new value', key2: null))

        options.allJvmArgs = []

        assertThat(options.systemProperties, equalTo([:]))

        options.allJvmArgs = ['-Dkey=value']

        assertThat(options.systemProperties, equalTo([key: 'value']))
    }

    @Test
    public void allJvmArgsIncludeMaxHeapSize() {
        options.maxHeapSize = '1g'
        options.jvmArgs('arg1')

        assertThat(options.allJvmArgs, equalTo(['arg1', '-Xmx1g']))
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
    public void canCopyToTargetOptions() {
        options.executable('executable')
        options.jvmArgs('arg')
        options.systemProperties(key: 12)
        options.maxHeapSize = '1g'

        JavaForkOptions target = context.mock(JavaForkOptions.class)
        context.checking {
            one(target).setExecutable('executable')
            one(target).setJvmArgs(['arg'])
            one(target).setSystemProperties(key: 12)
            one(target).setMaxHeapSize('1g')
            ignoring(target)
        }

        options.copyTo(target)
    }
}


