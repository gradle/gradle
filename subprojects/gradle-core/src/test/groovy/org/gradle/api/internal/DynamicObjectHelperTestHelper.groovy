/*
 * Copyright 2008 the original author or authors.
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

import static org.junit.Assert.*

public class DynamicObjectHelperTestHelper {
    public static void assertCanGetAllProperties (DynamicObjectHelperTest.Bean bean) {
        bean.readWriteProperty = 'readWrite'
        bean.setProperty('additional', 'additional')
        assertEquals(bean.getProperties().readWriteProperty, 'readWrite')
        assertEquals(bean.getProperties().additional, 'additional')
    }

    public static void assertCanGetProperties (DynamicObjectHelperTest.Bean bean) {
        bean.readWriteProperty = 'value'
        assertEquals(bean.readWriteProperty, 'value')

        bean.doSetReadOnlyProperty('value')
        assertEquals(bean.readOnlyProperty, 'value')

        bean.helper.additionalProperties.additional = 'value'
        assertEquals(bean.additional, 'value')

        bean.setProperty 'another', 'value'
        assertEquals(bean.another, 'value')
    }
    
    public static void assertCanGetAndSetProperties (DynamicObjectHelperTest.Bean bean) {
        bean.readWriteProperty = 'value'
        assertEquals(bean.readWriteProperty, 'value')

        bean.doSetReadOnlyProperty('value')
        assertEquals(bean.readOnlyProperty, 'value')

        bean.additional = 'value'
        assertEquals(bean.additional, 'value')

        bean.setProperty 'another', 'value'
        assertEquals(bean.another, 'value')
    }

    public static void assertCanCallMethods (DynamicObjectHelperTest.Bean bean) {
        assertEquals(bean.javaMethod('a', 'b'), 'java:a.b')
        assertTrue(bean.hasMethod('conventionMethod', 'a', 'b'))
        assertEquals(bean.conventionMethod('a', 'b'), 'convention:a.b')
    }
}

public class DynamicBean extends DynamicObjectHelperTest.Bean {
    def propertyMissing(String name) {
        super.getProperty(name)
    }

//    def methodMissing(String name, params) {
//        super.methodMissing(name, params)
//    }

    void setProperty(String name, Object value) {
        super.setProperty(name, value)
    }
}

public class GroovyBean extends DynamicBean {
    String groovyProperty

    def GroovyBean() {
        Map values = [:]
        ExpandoMetaClass metaClass = new ExpandoMetaClass(GroovyBean.class, false)
        metaClass.getDynamicGroovyProperty << {-> values.dynamicGroovyProperty }
        metaClass.setDynamicGroovyProperty << {value -> values.dynamicGroovyProperty = value}
        metaClass.dynamicGroovyMethod << {a, b -> "dynamicGroovy:$a.$b".toString() }
        metaClass.initialize()
        setMetaClass(metaClass)
    }

    def groovyMethod(a, b) {
        "groovy:$a.$b".toString()
    }
}