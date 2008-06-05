/*
 * Copyright 2007 the original author or authors.
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

package org.gradle.api.internal

import groovy.mock.interceptor.MockFor

/**
 * @author Hans Dockter
 */
class ConventionTestHelper  {
    MockFor conventionAwareHelperMocker

    Map expectedConventionMapping
    def expectedConvention
    def expectedProperty
    String expectedPropertyName

    ConventionTestHelper() {
        conventionAwareHelperMocker = new MockFor(ConventionAwareHelper)
        expectedConventionMapping = [:]
        expectedConvention = new Object()
        expectedProperty = new Object()
        expectedProperty = 'somepropname'
    }

    void checkAll(IConventionAware conventionAwareObject) {
        checkConventionMapping(conventionAwareObject)
        checkProperty(conventionAwareObject)
        checkSetGetConventionMapping(conventionAwareObject)
    }

    void checkConventionMapping(IConventionAware conventionAware) {
        conventionAwareHelperMocker.demand.conventionMapping(1..1) { Map conventionMapping ->
            assert conventionMapping.is(expectedConventionMapping)
            conventionAware
        }
        conventionAwareHelperMocker.use(conventionAware.conventionAwareHelper) {
            assert conventionAware.is(conventionAware.conventionMapping(expectedConventionMapping))
        }
    }

    void checkProperty(IConventionAware conventionAware) {
        conventionAwareHelperMocker.demand.getValue(1..1) { String propertyName ->
            assert propertyName == expectedPropertyName
            expectedProperty
        }
        conventionAwareHelperMocker.use(conventionAware.conventionAwareHelper) {
            assert conventionAware.getProperty(expectedPropertyName).is(expectedProperty)
        }
    }
      
    void checkSetGetConventionMapping(IConventionAware conventionAware) {
        conventionAwareHelperMocker.demand.setConventionMapping(1..1) { conventionMapping ->
            assert conventionMapping.is(expectedConventionMapping)
        }
        conventionAwareHelperMocker.demand.getValue(1..1) { String conventionMappingPropertyName ->
            assert conventionMappingPropertyName == 'conventionMapping'
            expectedConventionMapping
        }
        conventionAwareHelperMocker.use(conventionAware.conventionAwareHelper) {
            conventionAware.conventionMapping = expectedConventionMapping
            assert conventionAware.conventionMapping.is(expectedConventionMapping)
        }
    }
}
