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
package org.gradle.util

import org.jmock.integration.junit4.JMock
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import static org.hamcrest.Matchers.*
import static org.junit.Assert.*

@RunWith(JMock.class)
class MultiParentClassLoaderTest {
    private final JUnit4GroovyMockery context = new JUnit4GroovyMockery()
    private ClassLoader parent1
    private ClassLoader parent2
    private MultiParentClassLoader loader

    @Before
    public void setup() {
        parent1 = context.mock(ClassLoader)
        parent2 = context.mock(ClassLoader)
        loader = new MultiParentClassLoader(parent1, parent2)
    }

    @Test
    public void loadsClassFromParentsInOrderSpecified() {
        Class stringClass = String.class
        Class integerClass = Integer.class

        context.checking {
            allowing(parent1).loadClass('string')
            will(returnValue(stringClass))
            allowing(parent1).loadClass('integer')
            will(throwException(new ClassNotFoundException()))
            allowing(parent2).loadClass('integer')
            will(returnValue(integerClass))
        }

        assertThat(loader.loadClass('string'), equalTo(String.class))
        assertThat(loader.loadClass('string', true), equalTo(String.class))
        assertThat(loader.loadClass('integer'), equalTo(Integer.class))
        assertThat(loader.loadClass('integer', true), equalTo(Integer.class))
    }

    @Test
    public void throwsCNFExceptionWhenClassNotFound() {
        context.checking {
            allowing(parent1).loadClass('string')
            will(throwException(new ClassNotFoundException()))
            allowing(parent2).loadClass('string')
            will(throwException(new ClassNotFoundException()))
        }

        try {
            loader.loadClass('string')
            fail()
        } catch (ClassNotFoundException e) {
            assertThat(e.message, equalTo('string not found.'))
        }
    }
    
    @Test
    public void loadsPackageFromParentsInOrderSpecified() {
        Package stringPackage = String.class.getPackage()
        Package listPackage = List.class.getPackage()

        context.checking {
            allowing(parent1).getPackage('string')
            will(returnValue(stringPackage))
            allowing(parent1).getPackage('list')
            will(returnValue(null))
            allowing(parent2).getPackage('list')
            will(returnValue(listPackage))
        }

        assertThat(loader.getPackage('string'), sameInstance(stringPackage))
        assertThat(loader.getPackage('list'), sameInstance(listPackage))
    }

    @Test
    public void containsUnionOfPackagesFromAllParents() {
        Package package1 = context.mock(Package.class, 'p1')
        Package package2 = context.mock(Package.class, 'p2')

        context.checking {
            allowing(parent1).getPackages()
            will(returnValue([package1] as Package[]))
            allowing(parent2).getPackages()
            will(returnValue([package2] as Package[]))
        }

        assertThat(loader.getPackages(), hasItemInArray(package1))
        assertThat(loader.getPackages(), hasItemInArray(package2))
    }

    @Test
    public void loadsResourceFromParentsInOrderSpecified() {
        URL resource1 = new File('res1').toURI().toURL()
        URL resource2 = new File('res2').toURI().toURL()

        context.checking {
            allowing(parent1).getResource('resource1')
            will(returnValue(resource1))
            allowing(parent1).getResource('resource2')
            will(returnValue(null))
            allowing(parent2).getResource('resource2')
            will(returnValue(resource2))
        }

        assertThat(loader.getResource('resource1'), equalTo(resource1))
        assertThat(loader.getResource('resource2'), equalTo(resource2))
    }
    
    @Test
    public void containsUnionOfResourcesFromAllParents() {
        URL resource1 = new File('res1').toURI().toURL()
        URL resource2 = new File('res2').toURI().toURL()

        context.checking {
            allowing(parent1).getResources('resource1')
            will(returnValue(Collections.enumeration([resource1])))
            allowing(parent2).getResources('resource1')
            will(returnValue(Collections.enumeration([resource2, resource1])))
        }

        Enumeration resources = loader.getResources('resource1')
        assertTrue(resources.hasMoreElements())
        assertThat(resources.nextElement(), sameInstance(resource1))
        assertTrue(resources.hasMoreElements())
        assertThat(resources.nextElement(), sameInstance(resource2))
        assertFalse(resources.hasMoreElements())
    }
}
