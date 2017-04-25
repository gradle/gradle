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

import org.gradle.api.internal.file.FileResolver
import org.gradle.internal.Factory
import org.gradle.process.ProcessForkOptions
import org.gradle.process.internal.DefaultProcessForkOptions
import org.gradle.util.JUnit4GroovyMockery
import org.jmock.integration.junit4.JMock
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

import static org.gradle.util.Matchers.isEmptyMap
import static org.hamcrest.Matchers.*
import static org.junit.Assert.assertThat

@RunWith(JMock.class)
public class DefaultProcessForkOptionsTest {
    private final JUnit4GroovyMockery context = new JUnit4GroovyMockery()
    private final FileResolver resolver = context.mock(FileResolver.class)
    private final Factory workingDir = context.mock(Factory.class)
    private DefaultProcessForkOptions options
    private final File baseDir = new File("base-dir")

    @Before
    public void setup() {
        context.checking {
            allowing(resolver).resolveLater(".")
            will(returnValue(workingDir))
        }
        options = new DefaultProcessForkOptions(resolver)
    }

    @Test
    public void defaultValues() {
        assertThat(options.executable, nullValue())
        assertThat(options.environment, not(isEmptyMap()))
    }

    @Test
    public void resolvesWorkingDirectoryOnGet() {
        context.checking {
            one(resolver).resolveLater(12)
            will(returnValue(workingDir))
        }

        options.workingDir = 12

        context.checking {
            one(workingDir).create()
            will(returnValue(baseDir))
        }

        assertThat(options.workingDir, equalTo(baseDir))
    }

    @Test
    public void convertsEnvironmentToString() {
        options.environment = [key1: 12, key2: "${1+2}", key3: null]

        assertThat(options.actualEnvironment, equalTo(key1: '12', key2: '3', key3: 'null'))
    }

    @Test
    public void canAddEnvironmentVariables() {
        options.environment = [:]

        assertThat(options.environment, equalTo([:]))

        options.environment('key', 12)

        assertThat(options.environment, equalTo([key: 12]))
        assertThat(options.actualEnvironment, equalTo([key: '12']))

        options.environment(key2: "value")

        assertThat(options.environment, equalTo([key: 12, key2: "value"]))
    }

    @Test
    public void canCopyToTargetOptions() {
        options.executable('executable')
        options.environment('key', 12)

        ProcessForkOptions target = context.mock(ProcessForkOptions.class)
        context.checking {
            one(target).setExecutable('executable' as Object)
            one(target).setWorkingDir(workingDir)
            one(target).setEnvironment(withParam(not(isEmptyMap())))
        }

        options.copyTo(target)
    }
}


