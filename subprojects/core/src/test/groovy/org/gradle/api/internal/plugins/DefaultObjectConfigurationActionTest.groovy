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
package org.gradle.api.internal.plugins

import static org.hamcrest.Matchers.*

import org.gradle.util.JUnit4GroovyMockery
import org.jmock.integration.junit4.JMock
import org.junit.runner.RunWith
import org.junit.Test
import org.gradle.api.internal.file.FileResolver
import org.gradle.configuration.ScriptPluginFactory
import org.gradle.configuration.ScriptPlugin

@RunWith(JMock.class)
public class DefaultObjectConfigurationActionTest {
    private final JUnit4GroovyMockery context = new JUnit4GroovyMockery()
    private final Object target = new Object()
    private final URI file = new URI('script:something')
    private final FileResolver resolver = context.mock(FileResolver.class)
    private final ScriptPluginFactory factory = context.mock(ScriptPluginFactory.class)
    private final ScriptPlugin configurer = context.mock(ScriptPlugin.class)
    private final DefaultObjectConfigurationAction action = new DefaultObjectConfigurationAction(resolver, factory, target)

    @Test
    public void doesNothingWhenNothingSpecified() {
        action.execute()
    }

    @Test
    public void appliesScriptsToDefaultTargetObject() {
        context.checking {
            one(resolver).resolveUri('script')
            will(returnValue(file))

            one(factory).create(withParam(notNullValue()))
            will(returnValue(configurer))

            one(configurer).apply(target)
        }

        action.from('script')
        action.execute()
    }

    @Test
    public void appliesScriptsToTargetObjects() {
        Object target1 = new Object()
        Object target2 = new Object()

        context.checking {
            one(resolver).resolveUri('script')
            will(returnValue(file))

            one(factory).create(withParam(notNullValue()))
            will(returnValue(configurer))

            one(configurer).apply(target1)
            one(configurer).apply(target2)
        }

        action.from('script')
        action.to(target1)
        action.to(target2)
        action.execute()
    }
    
    @Test
    public void flattensCollections() {
        Object target1 = new Object()
        Object target2 = new Object()

        context.checking {
            one(resolver).resolveUri('script')
            will(returnValue(file))

            one(factory).create(withParam(notNullValue()))
            will(returnValue(configurer))

            one(configurer).apply(target1)
            one(configurer).apply(target2)
        }

        action.from('script')
        action.to([[target1], target2])
        action.execute()
    }
}

