/*
 * Copyright 2018 the original author or authors.
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
package org.gradle.internal.extensibility

import static org.junit.Assert.assertEquals
import static org.junit.Assert.assertTrue

public class ExtensibleDynamicObjectTestHelper {
    public static void assertCanGetAllProperties (ExtensibleDynamicObjectTest.Bean bean) {
        bean.readWriteProperty = 'readWrite'
        bean.defineProperty('additional', 'additional')
        assertEquals(bean.getProperties().readWriteProperty, 'readWrite')
        assertEquals(bean.getProperties().additional, 'additional')
    }

    public static void assertCanGetProperties (ExtensibleDynamicObjectTest.Bean bean) {
        bean.readWriteProperty = 'value'
        assertEquals(bean.readWriteProperty, 'value')

        bean.doSetReadOnlyProperty('value')
        assertEquals(bean.readOnlyProperty, 'value')

        bean.defineProperty('additional', 'value')
        assertEquals(bean.additional, 'value')
    }
    
    public static void assertCanGetAndSetProperties (ExtensibleDynamicObjectTest.Bean bean) {
        bean.readWriteProperty = 'value'
        assertEquals(bean.readWriteProperty, 'value')

        bean.doSetReadOnlyProperty('value')
        assertEquals(bean.readOnlyProperty, 'value')

        bean.ext.additional = 'value'
        assertEquals(bean.additional, 'value')
    }

    public static void assertCanCallMethods (ExtensibleDynamicObjectTest.Bean bean) {
        assertEquals(bean.javaMethod('a', 'b'), 'java:a.b')
        assertTrue(bean.hasMethod('conventionMethod', 'a', 'b'))
        assertEquals(bean.conventionMethod('a', 'b'), 'convention:a.b')
    }

    public static void decorateGroovyBean(bean) {
        Map values = [:]
        bean.metaClass.getDynamicGroovyProperty << {-> values.dynamicGroovyProperty }
        bean.metaClass.setDynamicGroovyProperty << {value -> values.dynamicGroovyProperty = value}
        bean.metaClass.dynamicGroovyMethod << {a, b -> "dynamicGroovy:$a.$b".toString() }
    }
}

public class DynamicBean extends ExtensibleDynamicObjectTest.Bean {
//    def propertyMissing(String name) {
//        super.getProperty(name)
//    }

//    def methodMissing(String name, params) {
//        super.methodMissing(name, params)
//    }

//    void setProperty(String name, Object value) {
//        super.setProperty(name, value)
//    }
}

public class GroovyBean extends DynamicBean {
    String groovyProperty

    def groovyMethod(a, b) {
        "groovy:$a.$b".toString()
    }
}

class DynamicGroovyBean {
    
    private holder = null;
    
    Map called = [:]
    
    def propertyMissing(String name) {
        if (name == "foo") {
            return holder;
        } else {
            throw new MissingPropertyException(name, getClass())
        }
    }

    def propertyMissing(String name, value) {
        if (name == "foo") {
            holder = value;
        } else {
            throw new MissingPropertyException(name, getClass())
        }

    }
    
    def methodMissing(String name, args) {
        if (name == "bar") {
            args[0] * 2   
        } else {
            throw new groovy.lang.MissingMethodException(name, getClass(), args)
        }
    }
}