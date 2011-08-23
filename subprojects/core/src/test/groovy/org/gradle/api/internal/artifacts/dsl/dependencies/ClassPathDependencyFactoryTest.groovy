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
package org.gradle.api.internal.artifacts.dsl.dependencies

import org.gradle.api.artifacts.Dependency
import org.gradle.api.file.FileCollection
import org.gradle.api.internal.ClassPathRegistry
import org.gradle.api.internal.Instantiator
import org.gradle.api.internal.artifacts.dependencies.DefaultSelfResolvingDependency
import org.gradle.api.internal.file.FileResolver
import org.gradle.util.JUnit4GroovyMockery
import org.jmock.integration.junit4.JMock
import org.junit.Test
import org.junit.runner.RunWith
import static org.hamcrest.Matchers.hasItemInArray
import static org.hamcrest.Matchers.sameInstance
import static org.junit.Assert.assertThat

@RunWith(JMock.class)
public class ClassPathDependencyFactoryTest {
    private final JUnit4GroovyMockery context = new JUnit4GroovyMockery()
    private final Instantiator instantiator = context.mock(Instantiator.class)
    private final ClassPathRegistry classPathRegistry = context.mock(ClassPathRegistry.class)
    private final FileResolver fileResolver = context.mock(FileResolver.class)
    private final ClassPathDependencyFactory factory = new ClassPathDependencyFactory(instantiator, classPathRegistry, fileResolver)

    @Test
    void createsDependencyForAClassPathNotation() {
        Dependency dependency = context.mock(Dependency.class)

        context.checking {
            Set files = []
            FileCollection fileCollection = context.mock(FileCollection.class)

            one(classPathRegistry).getClassPathFiles('GRADLE_API')
            will(returnValue(files))

            one(fileResolver).resolveFiles(withParam(hasItemInArray(files)))
            will(returnValue(fileCollection))

            one(instantiator).newInstance(DefaultSelfResolvingDependency.class, fileCollection)
            will(returnValue(dependency))
        }

        assertThat(factory.createDependency(Dependency.class, DependencyFactory.ClassPathNotation.GRADLE_API), sameInstance(dependency))
    }
}


