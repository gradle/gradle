/*
 * Copyright 2007-2008 the original author or authors.
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
 
package org.gradle.api.plugins

import org.gradle.api.internal.project.DefaultProject

/**
 * @author Hans Dockter
 */
class ConventionTest extends GroovyTestCase {
    Convention convention

    TestPluginConvention1 convention1
    TestPluginConvention2 convention2 = new TestPluginConvention2()

    DefaultProject testProject

    void setUp() {
        testProject = new DefaultProject()
        convention = new Convention(testProject)
        convention1 = new TestPluginConvention1()
        convention2 = new TestPluginConvention2() 
        convention.plugins.plugin1 = convention1
        convention.plugins.plugin2 = convention2
    }

    void testInit() {
        assert convention.project.is(testProject)
    }

    void testGetProperties() {
        assertEquals(convention1.a, convention.plugins.plugin1.a)
        assertEquals(convention1.a, convention.a)
    }

    void testGetPropertiesWithAmbiguity() {
        assertEquals(convention1.a, convention.plugins.plugin1.a)
        assertEquals(convention2.a, convention.plugins.plugin2.a)
        assert convention.a == convention1.a || convention.a == convention2.a
    }

    void testSetProperties() {
        convention.b = 'newvalue'
        assertEquals('newvalue', convention.plugins.plugin1.b)
    }

    void testSetPropertiesWithAmbiguity() {
        convention.a = 'newvalue'
        assert convention1.a == 'newvalue' || convention2.a == 'newvalue'
    }

    void testMissingProperties() {
        shouldFail(MissingPropertyException) {
            convention.prop
        }
        shouldFail(MissingPropertyException) {
            convention.prop  = 'newvalue'
        }
    }

    void testMethods() {
        assertEquals(convention1.meth('somearg'), convention.plugins.plugin1.meth('somearg'))
        assertEquals(convention1.meth('somearg'), convention.meth('somearg'))
    }

    void testMethodsWithAmbiguity() {
        assertEquals(convention1.meth(), convention.plugins.plugin1.meth())
        assertEquals(convention2.meth(), convention.plugins.plugin2.meth())
        assert convention.meth() == convention1.meth() || convention.meth() == convention2.meth()
    }

    void testMissingMethod() {
        shouldFail(MissingMethodException) {
            convention.methUnknown()
        }
    }
}
