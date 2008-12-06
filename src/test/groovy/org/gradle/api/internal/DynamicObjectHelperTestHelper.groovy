package org.gradle.api.internal

import static org.junit.Assert.*

public class DynamicObjectHelperTestHelper {
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
//    def propertyMissing(String name) {
//        property(name)
//    }

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
        metaClass.dynamicGroovyMethod << {a,b -> "dynamicGroovy:$a.$b".toString() }
        metaClass.initialize()
        this.metaClass = metaClass
    }

    def groovyMethod(a, b) {
        "groovy:$a.$b".toString()
    }

}