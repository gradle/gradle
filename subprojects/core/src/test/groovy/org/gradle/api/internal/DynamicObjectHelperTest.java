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
package org.gradle.api.internal;

import groovy.lang.*;
import groovy.lang.MissingMethodException;
import org.gradle.api.plugins.Convention;
import org.gradle.api.internal.plugins.DefaultConvention;
import org.gradle.util.HelperUtil;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;
import org.junit.Test;

import java.util.Map;

public class DynamicObjectHelperTest {
    @Test
    public void hasPropertiesDefinedByClass() {
        Bean bean = new Bean();
        assertTrue(bean.hasProperty("readWriteProperty"));
        assertTrue(bean.hasProperty("readOnlyProperty"));
        assertTrue(bean.hasProperty("writeOnlyProperty"));
    }

    @Test
    public void canGetAndSetClassProperty() {
        Bean bean = new Bean();
        bean.setReadWriteProperty("value");

        assertThat(bean.getProperty("readWriteProperty"), equalTo((Object) "value"));

        bean.setProperty("readWriteProperty", "new value");

        assertThat(bean.getProperty("readWriteProperty"), equalTo((Object) "new value"));
        assertThat(bean.getReadWriteProperty(), equalTo((Object) "new value"));
    }

    @Test
    public void canGetReadOnlyClassProperty() {
        Bean bean = new Bean();
        bean.doSetReadOnlyProperty("value");

        assertThat(bean.getProperty("readOnlyProperty"), equalTo((Object) "value"));
    }

    @Test
    public void cannotSetReadOnlyClassProperty() {
        Bean bean = new Bean();

        try {
            bean.setProperty("readOnlyProperty", "value");
            fail();
        } catch (ReadOnlyPropertyException e) {
            assertThat(e.getMessage(), equalTo("Cannot set the value of read-only property 'readOnlyProperty' on <bean>."));
        }
    }
    
    @Test
    public void canSetWriteOnlyClassProperty() {
        Bean bean = new Bean();
        bean.setProperty("writeOnlyProperty", "value");
        assertThat(bean.doGetWriteOnlyProperty(), equalTo("value"));
    }

    @Test
    public void cannotGetWriteOnlyClassProperty() {
        Bean bean = new Bean();

        try {
            bean.getProperty("writeOnlyProperty");
            fail();
        } catch (GroovyRuntimeException e) {
            assertThat(e.getMessage(), equalTo("Cannot get the value of write-only property 'writeOnlyProperty' on <bean>."));
        }
    }

    @Test
    public void canSetPropertyWhenGetterAndSetterHaveDifferentTypes() {
        Bean bean = new Bean();

        bean.setProperty("differentTypesProperty", "91");
        assertThat(bean.getProperty("differentTypesProperty"), equalTo((Object) 91));
    }

    @Test
    public void groovyObjectHasPropertiesDefinedByClassMetaInfo() {
        GroovyBean bean = new GroovyBean();
        assertTrue(bean.hasProperty("groovyProperty"));
        assertTrue(bean.hasProperty("dynamicGroovyProperty"));
    }

    @Test
    public void groovyObjectHasPropertiesInheritedFromSuperClass() {
        GroovyBean bean = new GroovyBean();
        assertTrue(bean.hasProperty("readWriteProperty"));
        assertTrue(bean.hasProperty("readOnlyProperty"));
        assertTrue(bean.hasProperty("writeOnlyProperty"));
    }

    @Test
    public void canGetAndSetGroovyObjectClassProperty() {
        GroovyBean bean = new GroovyBean();
        bean.setGroovyProperty("value");

        assertThat(bean.getProperty("groovyProperty"), equalTo((Object) "value"));

        bean.setProperty("groovyProperty", "new value");

        assertThat(bean.getProperty("groovyProperty"), equalTo((Object) "new value"));
        assertThat(bean.getGroovyProperty(), equalTo((Object) "new value"));
    }

    @Test
    public void canGetAndSetGroovyDynamicProperty() {
        GroovyBean bean = new GroovyBean();

        assertThat(bean.getProperty("dynamicGroovyProperty"), equalTo(null));

        bean.setProperty("dynamicGroovyProperty", "new value");

        assertThat(bean.getProperty("dynamicGroovyProperty"), equalTo((Object) "new value"));
    }

    @Test
    public void canGetButNotSetPropertiesOnJavaObjectFromGroovy() {
        DynamicObjectHelperTestHelper.assertCanGetProperties(new Bean());
    }
    
    @Test
    public void canGetAndSetPropertiesOnGroovyObjectFromGroovy() {
        DynamicObjectHelperTestHelper.assertCanGetAndSetProperties(new GroovyBean());
    }

    @Test
    public void canGetAndSetPropertiesOnGroovyObjectFromJava() {
        assertCanGetAndSetProperties(new GroovyBean());
    }

    @Test
    public void canGetAndSetPropertiesOnJavaSubClassOfGroovyObjectFromJava() {
        assertCanGetAndSetProperties(new DynamicBean());
    }

    private void assertCanGetAndSetProperties(DynamicObject bean) {
        bean.setProperty("readWriteProperty", "value");
        assertThat(bean.getProperty("readWriteProperty"), equalTo((Object) "value"));
        bean.setProperty("groovyProperty", "value");
        assertThat(bean.getProperty("groovyProperty"), equalTo((Object) "value"));
        bean.setProperty("additional", "value");
        assertThat(bean.getProperty("additional"), equalTo((Object) "value"));
    }

    @Test
    public void canGetAndSetPropertiesOnJavaSubClassOfGroovyObjectFromGroovy() {
        DynamicObjectHelperTestHelper.assertCanGetAndSetProperties(new DynamicBean());
    }

    @Test
    public void hasPropertyDefinedByConventionObject() {
        Bean bean = new Bean();
        Convention convention = new DefaultConvention();

        assertFalse(bean.hasProperty("conventionProperty"));

        bean.setConvention(convention);
        assertFalse(bean.hasProperty("conventionProperty"));

        convention.getPlugins().put("test", new ConventionBean());
        assertTrue(bean.hasProperty("conventionProperty"));
    }

    @Test
    public void canGetAndSetPropertyDefinedByConventionObject() {
        Bean bean = new Bean();
        Convention convention = new DefaultConvention();
        bean.setConvention(convention);
        ConventionBean conventionBean = new ConventionBean();
        convention.getPlugins().put("test", conventionBean);

        conventionBean.setConventionProperty("value");

        assertThat(bean.getProperty("conventionProperty"), equalTo((Object) "value"));

        bean.setProperty("conventionProperty", "new value");

        assertThat(bean.getProperty("conventionProperty"), equalTo((Object) "new value"));
        assertThat(conventionBean.getConventionProperty(), equalTo((Object) "new value"));
    }

    @Test
    public void hasPropertyDefinedByParent() {
        Bean parent = new Bean();
        parent.setProperty("parentProperty", "value");

        Bean bean = new Bean();
        assertFalse(bean.hasProperty("parentProperty"));

        bean.setParent(parent);
        assertTrue(bean.hasProperty("parentProperty"));
    }

    @Test
    public void canGetPropertyDefinedByParent() {
        Bean parent = new Bean();
        parent.setProperty("parentProperty", "value");

        Bean bean = new Bean();
        bean.setParent(parent);

        assertThat(bean.getProperty("parentProperty"), equalTo((Object) "value"));
    }

    @Test
    public void cannotSetPropertyDefinedByParent() {
        Bean parent = new Bean();

        Bean bean = new Bean();
        bean.setParent(parent);
        bean.setProperty("parentProperty", "value");

        assertFalse(parent.hasProperty("parentProperty"));
    }

    @Test
    public void hasAdditionalProperty() {
        Bean bean = new Bean();

        assertFalse(bean.hasProperty("additional"));

        bean.setProperty("additional", "value");
        assertTrue(bean.hasProperty("additional"));

        bean.setProperty("additional", null);
        assertTrue(bean.hasProperty("additional"));
    }

    @Test
    public void canGetAndSetAdditionalProperty() {
        Bean bean = new Bean();

        bean.setProperty("additional", "value");
        assertThat(bean.getProperty("additional"), equalTo((Object) "value"));
    }

    @Test
    public void canGetAndSetPropertyDefinedByAdditionalObject() {
        Bean otherObject = new Bean();
        otherObject.setProperty("otherObject", "value");

        Bean bean = new Bean();
        bean.helper.addObject(otherObject, DynamicObjectHelper.Location.BeforeConvention);

        assertTrue(bean.hasProperty("otherObject"));
        assertThat(bean.getProperty("otherObject"), equalTo((Object) "value"));
        bean.setProperty("otherObject", "new value");

        assertThat(otherObject.getProperty("otherObject"), equalTo((Object) "new value"));
    }
    
    @Test
    public void classPropertyTakesPrecedenceOverAdditionalProperty() {
        Bean bean = new Bean();
        bean.setReadWriteProperty("value");
        bean.helper.getAdditionalProperties().put("readWriteProperty", "additional");

        assertThat(bean.getProperty("readWriteProperty"), equalTo((Object) "value"));

        bean.setProperty("readWriteProperty", "new value");

        assertThat(bean.getProperty("readWriteProperty"), equalTo((Object) "new value"));
        assertThat(bean.getReadWriteProperty(), equalTo((Object) "new value"));
        assertThat(bean.helper.getAdditionalProperties().get("readWriteProperty"), equalTo((Object) "additional"));
    }

    @Test
    public void additionalPropertyTakesPrecedenceOverConventionProperty() {
        Bean bean = new Bean();
        bean.setProperty("conventionProperty", "value");

        Convention convention = new DefaultConvention();
        bean.setConvention(convention);
        ConventionBean conventionBean = new ConventionBean();
        convention.getPlugins().put("test", conventionBean);

        assertThat(bean.getProperty("conventionProperty"), equalTo((Object) "value"));

        bean.setProperty("conventionProperty", "new value");

        assertThat(bean.getProperty("conventionProperty"), equalTo((Object) "new value"));
        assertThat(bean.helper.getAdditionalProperties().get("conventionProperty"), equalTo((Object) "new value"));
        assertThat(conventionBean.getConventionProperty(), nullValue());
    }

    @Test
    public void conventionPropertyTakesPrecedenceOverParentProperty() {
        Bean parent = new Bean();
        parent.setProperty("conventionProperty", "parent");

        Bean bean = new Bean();
        bean.setParent(parent);

        Convention convention = new DefaultConvention();
        bean.setConvention(convention);
        ConventionBean conventionBean = new ConventionBean();
        conventionBean.setConventionProperty("value");
        convention.getPlugins().put("test", conventionBean);

        assertThat(bean.getProperty("conventionProperty"), equalTo((Object) "value"));
    }

    @Test
    public void canGetAllProperties() {
        Bean parent = new Bean();
        parent.setProperty("parentProperty", "parentProperty");
        parent.setReadWriteProperty("ignore me");
        parent.doSetReadOnlyProperty("ignore me");
        Convention parentConvention = new DefaultConvention();
        parentConvention.getPlugins().put("parent", new ConventionBean());
        parent.setConvention(parentConvention);

        GroovyBean bean = new GroovyBean();
        bean.setProperty("additional", "additional");
        bean.setReadWriteProperty("readWriteProperty");
        bean.doSetReadOnlyProperty("readOnlyProperty");
        bean.setGroovyProperty("groovyProperty");
        Convention convention = new DefaultConvention();
        ConventionBean conventionBean = new ConventionBean();
        conventionBean.setConventionProperty("conventionProperty");
        convention.getPlugins().put("bean", conventionBean);
        bean.setConvention(convention);
        bean.setParent(parent);

        Map<String, Object> properties = bean.getProperties();
        assertThat(properties.get("properties"), sameInstance((Object) properties));
        assertThat(properties.get("readWriteProperty"), equalTo((Object) "readWriteProperty"));
        assertThat(properties.get("readOnlyProperty"), equalTo((Object) "readOnlyProperty"));
        assertThat(properties.get("parentProperty"), equalTo((Object) "parentProperty"));
        assertThat(properties.get("additional"), equalTo((Object) "additional"));
        assertThat(properties.get("groovyProperty"), equalTo((Object) "groovyProperty"));
        assertThat(properties.get("groovyDynamicProperty"), equalTo(null));
        assertThat(properties.get("conventionProperty"), equalTo((Object) "conventionProperty"));
    }

    @Test
    public void canGetAllPropertiesFromGroovy() {
        DynamicObjectHelperTestHelper.assertCanGetAllProperties(new Bean());
        DynamicObjectHelperTestHelper.assertCanGetAllProperties(new GroovyBean());
        DynamicObjectHelperTestHelper.assertCanGetAllProperties(new DynamicBean());
    }

    @Test
    public void getPropertyFailsForUnknownProperty() {
        Bean bean = new Bean();

        try {
            bean.getProperty("unknown");
            fail();
        } catch (MissingPropertyException e) {
            assertThat(e.getMessage(), equalTo("Could not find property 'unknown' on <bean>."));
        }

        bean.setParent(new Bean(){
            @Override
            public String toString() {
                return "<parent>";
            }
        });

        try {
            bean.getProperty("unknown");
            fail();
        } catch (MissingPropertyException e) {
            assertThat(e.getMessage(), equalTo("Could not find property 'unknown' on <bean>."));
        }
    }

    @Test
    public void additionalPropertyWithNullValueIsNotTreatedAsUnknown() {
        Bean bean = new Bean();
        bean.setProperty("additional", null);
        assertThat(bean.getProperty("additional"), nullValue());
    }

    @Test
    public void canInvokeMethodDefinedByClass() {
        Bean bean = new Bean();
        assertTrue(bean.hasMethod("javaMethod", "a", "b"));
        assertThat(bean.invokeMethod("javaMethod", "a", "b"), equalTo((Object) "java:a.b"));
    }

    @Test
    public void canInvokeMethodDefinedByMetaClass() {
        Bean bean = new GroovyBean();

        assertTrue(bean.hasMethod("groovyMethod", "a", "b"));
        assertThat(bean.invokeMethod("groovyMethod", "a", "b"), equalTo((Object) "groovy:a.b"));

        assertTrue(bean.hasMethod("dynamicGroovyMethod", "a", "b"));
        assertThat(bean.invokeMethod("dynamicGroovyMethod", "a", "b"), equalTo((Object) "dynamicGroovy:a.b"));
    }

    @Test
    public void canInvokeMethodDefinedByScriptObject() {
        Bean bean = new Bean();
        Script script = HelperUtil.createScript("def scriptMethod(a, b) { \"script:$a.$b\" } ");
        bean.helper.addObject(new BeanDynamicObject(script), DynamicObjectHelper.Location.BeforeConvention);

        assertTrue(bean.hasMethod("scriptMethod", "a", "b"));
        assertThat(bean.invokeMethod("scriptMethod", "a", "b").toString(), equalTo((Object) "script:a.b"));
    }

    @Test
    public void canInvokeMethodDefinedByConvention() {
        Bean bean = new Bean();
        Convention convention = new DefaultConvention();
        convention.getPlugins().put("bean", new ConventionBean());

        assertFalse(bean.hasMethod("conventionMethod", "a", "b"));

        bean.setConvention(convention);

        assertTrue(bean.hasMethod("conventionMethod", "a", "b"));
        assertThat(bean.invokeMethod("conventionMethod", "a", "b"), equalTo((Object) "convention:a.b"));
    }

    @Test
    public void canInvokeMethodDefinedByParent() {
        Bean parent = new Bean() {
            public String parentMethod(String a, String b) {
                return String.format("parent:%s.%s", a, b);
            }
        };
        Bean bean = new Bean();

        assertFalse(bean.hasMethod("parentMethod", "a", "b"));
        
        bean.setParent(parent);

        assertTrue(bean.hasMethod("parentMethod", "a", "b"));
        assertThat(bean.invokeMethod("parentMethod", "a", "b"), equalTo((Object) "parent:a.b"));
    }

    @Test
    public void canInvokeMethodsOnJavaObjectFromGroovy() {
        Bean bean = new Bean();
        Convention convention = new DefaultConvention();
        bean.setConvention(convention);
        convention.getPlugins().put("bean", new ConventionBean());
        DynamicObjectHelperTestHelper.assertCanCallMethods(bean);
    }

    @Test
    public void canInvokeMethodsOnGroovyObjectFromGroovy() {
        GroovyBean bean = new GroovyBean();
        Convention convention = new DefaultConvention();
        bean.setConvention(convention);
        convention.getPlugins().put("bean", new ConventionBean());
        DynamicObjectHelperTestHelper.assertCanCallMethods(bean);
    }

    @Test
    public void canInvokeMethodsOnJavaSubClassOfGroovyObjectFromGroovy() {
        DynamicBean bean = new DynamicBean();
        Convention convention = new DefaultConvention();
        bean.setConvention(convention);
        convention.getPlugins().put("bean", new ConventionBean());
        DynamicObjectHelperTestHelper.assertCanCallMethods(bean);
    }

    @Test
    public void canInvokeClosurePropertyAsAMethod() {
        Bean bean = new Bean();
        bean.setProperty("someMethod", HelperUtil.toClosure("{ param -> param.toLowerCase() }"));
        assertThat(bean.invokeMethod("someMethod", "Param"), equalTo((Object) "param"));
    }
    
    @Test
    public void invokeMethodFailsForUnknownMethod() {
        Bean bean = new Bean();
        try {
            bean.invokeMethod("unknown", "a", 12);
            fail();
        } catch (MissingMethodException e) {
            assertThat(e.getMessage(), equalTo("Could not find method unknown() for arguments [a, 12] on <bean>."));
        }
    }

    @Test
    public void propagatesGetPropertyException() {
        final RuntimeException failure = new RuntimeException();
        Bean bean = new Bean() {
            String getFailure() {
                throw failure;
            }
        };

        try {
            bean.getProperty("failure");
            fail();
        } catch (Exception e) {
            assertThat(e, sameInstance((Exception) failure));
        }
    }

    @Test
    public void propagatesSetPropertyException() {
        final RuntimeException failure = new RuntimeException();
        Bean bean = new Bean() {
            void setFailure(String value) {
                throw failure;
            }
        };

        try {
            bean.setProperty("failure", "a");
            fail();
        } catch (Exception e) {
            assertThat(e, sameInstance((Exception) failure));
        }
    }

    @Test
    public void propagatesInvokeMethodException() {
        final RuntimeException failure = new RuntimeException();
        Bean bean = new Bean() {
            void failure() {
                throw failure;
            }
        };

        try {
            bean.invokeMethod("failure");
            fail();
        } catch (Exception e) {
            assertThat(e, sameInstance((Exception) failure));
        }
    }

    @Test
    public void additionalPropertiesAreInherited() {
        Bean bean = new Bean();
        bean.setProperty("additional", "value");

        DynamicObject inherited = bean.getInheritable();
        assertTrue(inherited.hasProperty("additional"));
        assertThat(inherited.getProperty("additional"), equalTo((Object) "value"));
        assertThat(inherited.getProperties().get("additional"), equalTo((Object) "value"));
    }

    @Test
    public void inheritedAdditionalPropertiesTrackChanges() {
        Bean bean = new Bean();

        DynamicObject inherited = bean.getInheritable();
        assertFalse(inherited.hasProperty("additional"));

        bean.setProperty("additional", "value");
        assertTrue(inherited.hasProperty("additional"));
        assertThat(inherited.getProperty("additional"), equalTo((Object) "value"));
    }

    @Test
    public void additionalObjectPropertiesAreInherited() {
        Bean other = new Bean();
        other.setProperty("other", "value");
        Bean bean = new Bean();
        bean.helper.addObject(other, DynamicObjectHelper.Location.BeforeConvention);

        DynamicObject inherited = bean.getInheritable();
        assertTrue(inherited.hasProperty("other"));
        assertThat(inherited.getProperty("other"), equalTo((Object) "value"));
        assertThat(inherited.getProperties().get("other"), equalTo((Object) "value"));
    }

    @Test
    public void inheritedAdditionalObjectPropertiesTrackChanges() {
        Bean other = new Bean();
        other.setProperty("other", "value");
        Bean bean = new Bean();

        DynamicObject inherited = bean.getInheritable();
        assertFalse(inherited.hasProperty("other"));

        bean.helper.addObject(other, DynamicObjectHelper.Location.BeforeConvention);

        assertTrue(inherited.hasProperty("other"));
        assertThat(inherited.getProperty("other"), equalTo((Object) "value"));
    }

    @Test
    public void conventionPropertiesAreInherited() {
        Bean bean = new Bean();
        Convention convention = new DefaultConvention();
        ConventionBean conventionBean = new ConventionBean();
        conventionBean.setConventionProperty("value");
        convention.getPlugins().put("convention", conventionBean);
        bean.setConvention(convention);

        DynamicObject inherited = bean.getInheritable();
        assertTrue(inherited.hasProperty("conventionProperty"));
        assertThat(inherited.getProperty("conventionProperty"), equalTo((Object) "value"));
        assertThat(inherited.getProperties().get("conventionProperty"), equalTo((Object) "value"));
    }

    @Test
    public void inheritedConventionPropertiesTrackChanges() {
        Bean bean = new Bean();

        DynamicObject inherited = bean.getInheritable();
        assertFalse(inherited.hasProperty("conventionProperty"));

        Convention convention = new DefaultConvention();
        ConventionBean conventionBean = new ConventionBean();
        conventionBean.setConventionProperty("value");
        convention.getPlugins().put("convention", conventionBean);
        bean.setConvention(convention);

        assertTrue(inherited.hasProperty("conventionProperty"));
        assertThat(inherited.getProperty("conventionProperty"), equalTo((Object) "value"));
    }

    @Test
    public void parentPropertiesAreInherited() {
        Bean parent = new Bean();
        parent.setProperty("parentProperty", "value");
        Bean bean = new Bean();
        bean.setParent(parent);

        DynamicObject inherited = bean.getInheritable();
        assertTrue(inherited.hasProperty("parentProperty"));
        assertThat(inherited.getProperty("parentProperty"), equalTo((Object) "value"));
        assertThat(inherited.getProperties().get("parentProperty"), equalTo((Object) "value"));
    }

    @Test
    public void otherPropertiesAreNotInherited() {
        Bean bean = new Bean();
        assertTrue(bean.hasProperty("readWriteProperty"));

        DynamicObject inherited = bean.getInheritable();
        assertFalse(inherited.hasProperty("readWriteProperty"));
        assertFalse(inherited.getProperties().containsKey("readWriteProperty"));
    }

    @Test
    public void cannotSetInheritedProperties() {
        Bean bean = new Bean();
        bean.setProperty("additional", "value");

        DynamicObject inherited = bean.getInheritable();
        try {
            inherited.setProperty("additional", "new value");
            fail();
        } catch (MissingPropertyException e) {
            assertThat(e.getMessage(), equalTo("Could not find property 'additional' inherited from <bean>."));
        }
    }

    @Test
    public void conventionMethodsAreInherited() {
        Bean bean = new Bean();
        Convention convention = new DefaultConvention();
        convention.getPlugins().put("convention", new ConventionBean());
        bean.setConvention(convention);

        DynamicObject inherited = bean.getInheritable();
        assertTrue(inherited.hasMethod("conventionMethod", "a", "b"));
        assertThat(inherited.invokeMethod("conventionMethod", "a", "b"), equalTo((Object) "convention:a.b"));
    }

    @Test
    public void additionalObjectMethodsAreInherited() {
        Bean other = new Bean();
        Convention convention = new DefaultConvention();
        convention.getPlugins().put("convention", new ConventionBean());
        other.setConvention(convention);

        Bean bean = new Bean();
        bean.helper.addObject(other, DynamicObjectHelper.Location.BeforeConvention);

        DynamicObject inherited = bean.getInheritable();
        assertTrue(inherited.hasMethod("conventionMethod", "a", "b"));
        assertThat(inherited.invokeMethod("conventionMethod", "a", "b"), equalTo((Object) "convention:a.b"));
    }

    @Test
    public void parentMethodsAreInherited() {
        Bean parent = new Bean();
        Convention convention = new DefaultConvention();
        convention.getPlugins().put("convention", new ConventionBean());
        parent.setConvention(convention);
        Bean bean = new Bean();
        bean.setParent(parent);

        DynamicObject inherited = bean.getInheritable();
        assertTrue(inherited.hasMethod("conventionMethod", "a", "b"));
        assertThat(inherited.invokeMethod("conventionMethod", "a", "b"), equalTo((Object) "convention:a.b"));
    }

    @Test
    public void otherMethodsAreNotInherited() {
        Bean bean = new Bean();
        assertTrue(bean.hasMethod("javaMethod", "a", "b"));

        DynamicObject inherited = bean.getInheritable();
        assertFalse(inherited.hasMethod("javaMethod", "a", "b"));
    }
    
    public static class Bean implements DynamicObject {
        private String readWriteProperty;
        private String readOnlyProperty;
        private String writeOnlyProperty;
        private Integer differentTypesProperty;
        final DynamicObjectHelper helper;

        public Bean() {
            helper = new DynamicObjectHelper(this);
        }

        @Override
        public String toString() {
            return "<bean>";
        }

        public void setConvention(Convention convention) {
            helper.setConvention(convention);
        }

        public void setParent(DynamicObject parent) {
            helper.setParent(parent);
        }

        public String getReadOnlyProperty() {
            return readOnlyProperty;
        }

        public void doSetReadOnlyProperty(String readOnlyProperty) {
            this.readOnlyProperty = readOnlyProperty;
        }

        public String doGetWriteOnlyProperty() {
            return writeOnlyProperty;
        }

        public void setWriteOnlyProperty(String writeOnlyProperty) {
            this.writeOnlyProperty = writeOnlyProperty;
        }

        public String getReadWriteProperty() {
            return readWriteProperty;
        }

        public void setReadWriteProperty(String property) {
            this.readWriteProperty = property;
        }

        public Integer getDifferentTypesProperty() {
            return differentTypesProperty;
        }

        public void setDifferentTypesProperty(Object differentTypesProperty) {
            this.differentTypesProperty = Integer.parseInt(differentTypesProperty.toString());
        }

        public String javaMethod(String a, String b) {
            return String.format("java:%s.%s", a, b);
        }
        
        public Object getProperty(String name) {
            return helper.getProperty(name);
        }

        public boolean hasProperty(String name) {
            return helper.hasProperty(name);
        }

        public void setProperty(String name, Object value) {
            helper.setProperty(name, value);
        }

        public Map<String, Object> getProperties() {
            return helper.getProperties();
        }

        public boolean hasMethod(String name, Object... arguments) {
            return helper.hasMethod(name, arguments);
        }

        public Object invokeMethod(String name, Object... arguments) {
            return helper.invokeMethod(name, arguments);
        }

        public Object methodMissing(String name, Object params) {
            return helper.invokeMethod(name, (Object[]) params);
        }

        public Object propertyMissing(String name) {
            return getProperty(name);
        }

        public DynamicObject getInheritable() {
            return helper.getInheritable();
        }
    }

    private static class DynamicBean extends GroovyBean {
    }

    private static class ConventionBean {
        private String conventionProperty;

        public String getConventionProperty() {
            return conventionProperty;
        }

        public void setConventionProperty(String conventionProperty) {
            this.conventionProperty = conventionProperty;
        }

        public String conventionMethod(String a, String b) {
            return String.format("convention:%s.%s", a, b);
        }
    }
}
