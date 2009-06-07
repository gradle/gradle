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

import org.junit.runner.RunWith
import org.jmock.integration.junit4.JMock
import org.gradle.util.JUnit4GroovyMockery
import org.apache.tools.ant.BuildListener
import org.junit.Test
import static org.junit.Assert.*
import static org.hamcrest.Matchers.*
import org.junit.Before
import org.gradle.util.HelperUtil
import org.gradle.api.Project
import org.apache.tools.ant.types.Path

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
        def ant = factory.createAntBuilder()
        assertThat(ant, notNullValue())
    }

    @Test
    public void setsBaseDirOfAntProject() {
        def ant = factory.createAntBuilder()
        assertThat(ant.project.baseDir, equalTo(project.projectDir))
    }

    @Test
    public void canGetAndSetAntProjectProperties() {
        def ant = factory.createAntBuilder()
        ant.someProp = 'value'
        ant.property(name: 'anotherProp', value: '${someProp}')
        assertThat(ant.anotherProp, equalTo('value'))
    }

    @Test
    public void canGetAndSetAntPropertiesUsingPropertiesNameSpace() {
        def ant = factory.createAntBuilder()
        ant.properties.someProp = 'value'
        ant.property(name: 'anotherProp', value: '${someProp}')
        assertThat(ant.properties['anotherProp'], equalTo('value'))
    }

    @Test
    public void throwsMissingPropertyExceptionForUnknownProperty() {
        def ant = factory.createAntBuilder()
        try {
            ant.unknown
            fail()
        } catch (MissingPropertyException e) {
            // expected
        }
    }

    @Test
    public void canGetAndSetAntProjectReferences() {
        def ant = factory.createAntBuilder()
        def path = new Path(ant.project, 'path')
        ant.references.someRef = path
        ant.path(id: 'anotherRef', refid: 'someRef')
        assertThat(ant.references['anotherRef'].toString(), equalTo(path.toString()))
    }
}