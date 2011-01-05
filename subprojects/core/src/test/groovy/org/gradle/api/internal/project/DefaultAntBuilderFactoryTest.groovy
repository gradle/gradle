/*
 * Copyright 2009 the original author or authors.
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
package org.gradle.api.internal.project

import org.apache.tools.ant.BuildListener
import org.gradle.api.Project
import org.gradle.util.HelperUtil
import org.gradle.util.JUnit4GroovyMockery
import org.jmock.integration.junit4.JMock
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import static org.hamcrest.Matchers.*
import static org.junit.Assert.*

@RunWith (JMock)
public class DefaultAntBuilderFactoryTest {
    private final JUnit4GroovyMockery context = new JUnit4GroovyMockery()
    private final BuildListener listener = context.mock(BuildListener)
    private final Project project = HelperUtil.createRootProject()
    private final DefaultAntBuilderFactory factory = new DefaultAntBuilderFactory(listener, project)

    @Before
    public void setUp() {
        context.checking {
            allowing(listener).messageLogged(withParam(anything()))
            allowing(listener).taskStarted(withParam(anything()))
            allowing(listener).taskFinished(withParam(anything()))
        }
    }

    @Test
    public void createsAnAntBuilder() {
        def ant = factory.create()
        assertThat(ant, notNullValue())
    }

    @Test
    public void setsBaseDirOfAntProject() {
        def ant = factory.create()
        assertThat(ant.project.baseDir, equalTo(project.projectDir))
    }
}