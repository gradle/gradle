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
package org.gradle.internal.extensibility;

import groovy.lang.GroovyObjectSupport;
import groovy.lang.GroovyRuntimeException;
import groovy.lang.MissingMethodException;
import groovy.lang.MissingPropertyException;
import groovy.lang.Script;
import org.gradle.api.plugins.Convention;
import org.gradle.internal.metaobject.BeanDynamicObject;
import org.gradle.internal.metaobject.DynamicObject;
import org.gradle.api.internal.DynamicObjectAware;
import org.gradle.internal.metaobject.DynamicObjectUtil;
import org.gradle.util.TestUtil;
import org.junit.Test;

import java.util.Map;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class ExtensibleDynamicObjectTest {
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
        } catch (GroovyRuntimeException e) {
            assertThat(e.getMessage(), equalTo("Cannot set the value of read-only property 'readOnlyProperty' for <bean> of type " + Bean.class.getName() + "."));
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
            assertThat(e.getMessage(), equalTo("Cannot get the value of write-only property 'writeOnlyProperty' for <bean> of type " + Bean.class.getName() + "."));
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
        ExtensibleDynamicObjectTestHelper.decorateGroovyBean(bean);
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

        assertThat(((Bean) bean).getProperty("groovyProperty"), equalTo((Object) "value"));

        bean.setProperty("groovyProperty", "new value");

        assertThat(((Bean) bean).getProperty("groovyProperty"), equalTo((Object) "new value"));
        assertThat(bean.getGroovyProperty(), equalTo((Object) "new value"));
    }

    @Test
    public void canGetAndSetGroovyDynamicProperty() {
        GroovyBean bean = new GroovyBean();
        ExtensibleDynamicObjectTestHelper.decorateGroovyBean(bean);

        assertThat(((Bean) bean).getProperty("dynamicGroovyProperty"), equalTo(null));

        bean.setProperty("dynamicGroovyProperty", "new value");

        assertThat(((Bean) bean).getProperty("dynamicGroovyProperty"), equalTo((Object) "new value"));
    }

    @Test
    public void canGetButNotSetPropertiesOnJavaObjectFromGroovy() {
        ExtensibleDynamicObjectTestHelper.assertCanGetProperties(new Bean());
    }

    @Test
    public void canGetAndSetPropertiesOnGroovyObjectFromGroovy() {
        ExtensibleDynamicObjectTestHelper.assertCanGetAndSetProperties(new GroovyBean());
    }

    @Test
    public void canGetAndSetPropertiesOnGroovyObjectFromJava() {
        assertCanGetAndSetProperties(new GroovyBean().getAsDynamicObject());
    }

    @Test
    public void canGetAndSetPropertiesOnJavaSubClassOfGroovyObjectFromJava() {
        assertCanGetAndSetProperties(new DynamicJavaBean().getAsDynamicObject());
    }

    private void assertCanGetAndSetProperties(DynamicObject bean) {
        bean.setProperty("readWriteProperty", "value");
        assertThat(bean.getProperty("readWriteProperty"), equalTo((Object) "value"));
        bean.setProperty("groovyProperty", "value");
        assertThat(bean.getProperty("groovyProperty"), equalTo((Object) "value"));
    }

    @Test
    public void canGetAndSetPropertiesOnJavaSubClassOfGroovyObjectFromGroovy() {
        ExtensibleDynamicObjectTestHelper.assertCanGetAndSetProperties(new DynamicJavaBean());
    }

    @Test
    public void hasPropertyDefinedByConventionObject() {
        Bean bean = new Bean();
        Convention convention = bean.extensibleDynamicObject.getConvention();

        assertFalse(bean.hasProperty("conventionProperty"));
        assertFalse(bean.hasProperty("conventionProperty"));

        convention.getPlugins().put("test", new ConventionBean());
        assertTrue(bean.hasProperty("conventionProperty"));
    }

    @Test
    public void canGetAndSetPropertyDefinedByConventionObject() {
        Bean bean = new Bean();
        Convention convention = bean.extensibleDynamicObject.getConvention();
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
        parent.defineProperty("parentProperty", "value");

        Bean bean = new Bean();
        assertFalse(bean.hasProperty("parentProperty"));

        bean.setParent(parent.getAsDynamicObject());
        assertTrue(bean.hasProperty("parentProperty"));
    }

    @Test
    public void canGetPropertyDefinedByParent() {
        Bean parent = new Bean();
        parent.defineProperty("parentProperty", "value");

        Bean bean = new Bean();
        bean.setParent(parent.getAsDynamicObject());

        assertThat(bean.getProperty("parentProperty"), equalTo((Object) "value"));
    }

    @Test
    public void extraPropertyIsNotVisibleToParent() {
        Bean parent = new Bean();

        Bean bean = new Bean();
        bean.setParent(parent.getAsDynamicObject());
        bean.defineProperty("parentProperty", "value");

        assertFalse(parent.hasProperty("parentProperty"));
    }

    @Test
    public void hasAdditionalProperty() {
        Bean bean = new Bean();

        assertFalse(bean.hasProperty("additional"));

        bean.defineProperty("additional", "value");
        assertTrue(bean.hasProperty("additional"));

        bean.setProperty("additional", null);
        assertTrue(bean.hasProperty("additional"));
    }

    @Test
    public void canGetAndSetExtraProperty() {
        Bean bean = new Bean();

        bean.defineProperty("additional", "value 1");
        assertThat(bean.getProperty("additional"), equalTo((Object) "value 1"));

        bean.setProperty("additional", "value 2");
        assertThat(bean.getProperty("additional"), equalTo((Object) "value 2"));
    }

    @Test
    public void canGetAndSetPropertyDefinedByAdditionalObject() {
        Bean otherObject = new Bean();
        otherObject.defineProperty("otherObject", "value");

        Bean bean = new Bean();
        bean.extensibleDynamicObject.addObject(otherObject.getAsDynamicObject(), ExtensibleDynamicObject.Location.BeforeConvention);

        assertTrue(bean.hasProperty("otherObject"));
        assertThat(bean.getProperty("otherObject"), equalTo((Object) "value"));
        bean.setProperty("otherObject", "new value");

        assertThat(otherObject.getProperty("otherObject"), equalTo((Object) "new value"));
    }

    @Test
    public void classPropertyTakesPrecedenceOverAdditionalProperty() {
        Bean bean = new Bean();
        bean.setReadWriteProperty("value");
        bean.extensibleDynamicObject.getDynamicProperties().set("readWriteProperty", "additional");

        assertThat(bean.getProperty("readWriteProperty"), equalTo((Object) "value"));

        bean.setProperty("readWriteProperty", "new value");

        assertThat(bean.getProperty("readWriteProperty"), equalTo((Object) "new value"));
        assertThat(bean.getReadWriteProperty(), equalTo((Object) "new value"));
        assertThat(bean.extensibleDynamicObject.getDynamicProperties().get("readWriteProperty"), equalTo((Object) "additional"));
    }

    @Test
    public void extraPropertyTakesPrecedenceOverConventionProperty() {
        Bean bean = new Bean();
        bean.defineProperty("conventionProperty", "value");

        Convention convention = bean.extensibleDynamicObject.getConvention();
        ConventionBean conventionBean = new ConventionBean();
        convention.getPlugins().put("test", conventionBean);

        assertThat(bean.getProperty("conventionProperty"), equalTo((Object) "value"));

        bean.setProperty("conventionProperty", "new value");

        assertThat(bean.getProperty("conventionProperty"), equalTo((Object) "new value"));
        assertThat(bean.extensibleDynamicObject.getDynamicProperties().get("conventionProperty"), equalTo((Object) "new value"));
        assertThat(conventionBean.getConventionProperty(), nullValue());
    }

    @Test
    public void conventionPropertyTakesPrecedenceOverParentProperty() {
        Bean parent = new Bean();
        parent.defineProperty("conventionProperty", "parent");

        Bean bean = new Bean();
        bean.setParent(parent.getAsDynamicObject());

        Convention convention = bean.extensibleDynamicObject.getConvention();
        ConventionBean conventionBean = new ConventionBean();
        conventionBean.setConventionProperty("value");
        convention.getPlugins().put("test", conventionBean);

        assertThat(bean.getProperty("conventionProperty"), equalTo((Object) "value"));
    }

    @Test
    public void canGetAllProperties() {
        Bean parent = new Bean();
        parent.defineProperty("parentProperty", "parentProperty");
        parent.setReadWriteProperty("ignore me");
        parent.doSetReadOnlyProperty("ignore me");
        Convention parentConvention = parent.extensibleDynamicObject.getConvention();
        parentConvention.getPlugins().put("parent", new ConventionBean());

        GroovyBean bean = new GroovyBean();
        bean.defineProperty("additional", "additional");
        bean.setReadWriteProperty("readWriteProperty");
        bean.doSetReadOnlyProperty("readOnlyProperty");
        bean.setGroovyProperty("groovyProperty");
        Convention convention = bean.extensibleDynamicObject.getConvention();
        ConventionBean conventionBean = new ConventionBean();
        conventionBean.setConventionProperty("conventionProperty");
        convention.getPlugins().put("bean", conventionBean);
        bean.setParent(parent.getAsDynamicObject());

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
        ExtensibleDynamicObjectTestHelper.assertCanGetAllProperties(new Bean());
        ExtensibleDynamicObjectTestHelper.assertCanGetAllProperties(new GroovyBean());
        ExtensibleDynamicObjectTestHelper.assertCanGetAllProperties(new DynamicJavaBean());
    }

    @Test
    public void getPropertyFailsForUnknownProperty() {
        Bean bean = new Bean();

        try {
            bean.getProperty("unknown");
            fail();
        } catch (MissingPropertyException e) {
            assertThat(e.getMessage(), equalTo("Could not get unknown property 'unknown' for <bean> of type " + Bean.class.getName() + "."));
        }

        bean.setParent(new Bean() {
            @Override
            public String toString() {
                return "<parent>";
            }
        }.getAsDynamicObject());

        try {
            bean.getProperty("unknown");
            fail();
        } catch (MissingPropertyException e) {
            assertThat(e.getMessage(), equalTo("Could not get unknown property 'unknown' for <bean> of type " + Bean.class.getName() + "."));
        }
    }

    @Test
    public void doesNotIncludeToStringInGetPropertyErrorMessageWhenItIsNotImplemented() {
        DynamicObject bean = new ExtensibleDynamicObject(new Object(), Object.class, TestUtil.instantiatorFactory().decorateLenient());

        try {
            bean.getProperty("unknown");
            fail();
        } catch (MissingPropertyException e) {
            assertThat(e.getMessage(), equalTo("Could not get unknown property 'unknown' for object of type java.lang.Object."));
        }
    }

    @Test
    public void setPropertyFailsForUnknownProperty() {
        Bean bean = new Bean();

        try {
            bean.setProperty("unknown", 12);
            fail();
        } catch (MissingPropertyException e) {
            assertThat(e.getMessage(), equalTo("Could not set unknown property 'unknown' for <bean> of type " + Bean.class.getName() + "."));
        }
    }

    @Test
    public void doesNotIncludeToStringInSetPropertyErrorMessageWhenItIsNotImplemented() {
        DynamicObject bean = new ExtensibleDynamicObject(new Object(), Object.class, TestUtil.instantiatorFactory().decorateLenient());

        try {
            bean.setProperty("unknown", "value");
            fail();
        } catch (MissingPropertyException e) {
            assertThat(e.getMessage(), equalTo("Could not set unknown property 'unknown' for object of type java.lang.Object."));
        }
    }

    @Test
    public void extraPropertyWithNullValueIsNotTreatedAsUnknown() {
        Bean bean = new Bean();
        bean.defineProperty("additional", null);
        assertThat(bean.getProperty("additional"), nullValue());
    }

    @Test
    public void canInvokeMethodDefinedByClass() {
        Bean bean = new Bean();
        assertTrue(bean.hasMethod("javaMethod", "a", "b"));
        assertThat(bean.getAsDynamicObject().invokeMethod("javaMethod", "a", "b"), equalTo((Object) "java:a.b"));
    }

    @Test
    public void canInvokeMethodDefinedByMetaClass() {
        Bean bean = new GroovyBean();
        ExtensibleDynamicObjectTestHelper.decorateGroovyBean(bean);

        assertTrue(bean.hasMethod("groovyMethod", "a", "b"));
        assertThat(bean.getAsDynamicObject().invokeMethod("groovyMethod", "a", "b"), equalTo((Object) "groovy:a.b"));

        assertTrue(bean.hasMethod("dynamicGroovyMethod", "a", "b"));
        assertThat(bean.getAsDynamicObject().invokeMethod("dynamicGroovyMethod", "a", "b"), equalTo((Object) "dynamicGroovy:a.b"));
    }

    @Test
    public void canInvokeMethodDefinedByScriptObject() {
        Bean bean = new Bean();
        Script script = TestUtil.createScript("def scriptMethod(a, b) { \"script:$a.$b\" } ");
        bean.extensibleDynamicObject.addObject(new BeanDynamicObject(script), ExtensibleDynamicObject.Location.BeforeConvention);

        assertTrue(bean.hasMethod("scriptMethod", "a", "b"));
        assertThat(bean.getAsDynamicObject().invokeMethod("scriptMethod", "a", "b").toString(), equalTo((Object) "script:a.b"));
    }

    @Test
    public void canInvokeMethodDefinedByConvention() {
        Bean bean = new Bean();
        Convention convention = bean.extensibleDynamicObject.getConvention();

        assertFalse(bean.hasMethod("conventionMethod", "a", "b"));

        convention.getPlugins().put("bean", new ConventionBean());
        assertTrue(bean.hasMethod("conventionMethod", "a", "b"));
        assertThat(bean.getAsDynamicObject().invokeMethod("conventionMethod", "a", "b"), equalTo((Object) "convention:a.b"));
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

        bean.setParent(parent.getAsDynamicObject());

        assertTrue(bean.hasMethod("parentMethod", "a", "b"));
        assertThat(bean.getAsDynamicObject().invokeMethod("parentMethod", "a", "b"), equalTo((Object) "parent:a.b"));
    }

    @Test
    public void canInvokeMethodsOnJavaObjectFromGroovy() {
        Bean bean = new Bean();
        Convention convention = bean.extensibleDynamicObject.getConvention();
        convention.getPlugins().put("bean", new ConventionBean());
        new ExtensibleDynamicObjectTestHelper().assertCanCallMethods(bean);
    }

    @Test
    public void canInvokeMethodsOnGroovyObjectFromGroovy() {
        GroovyBean bean = new GroovyBean();
        Convention convention = bean.extensibleDynamicObject.getConvention();
        convention.getPlugins().put("bean", new ConventionBean());
        new ExtensibleDynamicObjectTestHelper().assertCanCallMethods(bean);
    }

    @Test
    public void canInvokeMethodsOnJavaSubClassOfGroovyObjectFromGroovy() {
        // This doesn't work.
        // It used to because at the bottom of the hierarchy chain the object implemented methodMissing().
        // However, our normal “decorated” classes do not do this so it is not realistic.

        // Groovy does something very strange here.
        // For some reason (probably because the class is Java), it won't employ any dynamism.
        // Even implementing invokeMethod at the Java level has no effect.

        DynamicJavaBean javaBean = new DynamicJavaBean();
        Convention convention = javaBean.extensibleDynamicObject.getConvention();
        convention.getPlugins().put("bean", new ConventionBean());
        new ExtensibleDynamicObjectTestHelper().assertCanCallMethods(javaBean);
    }

    @Test
    public void canInvokeClosurePropertyAsAMethod() {
        Bean bean = new Bean();
        bean.defineProperty("someMethod", TestUtil.toClosure("{ param -> param.toLowerCase() }"));
        assertThat(bean.invokeMethod("someMethod", "Param"), equalTo((Object) "param"));
    }

    @Test
    public void invokeMethodFailsForUnknownMethod() {
        Bean bean = new Bean();
        try {
            bean.getAsDynamicObject().invokeMethod("unknown", "a", 12);
            fail();
        } catch (MissingMethodException e) {
            assertThat(e.getMessage(), equalTo("Could not find method unknown() for arguments [a, 12] on <bean> of type " + Bean.class.getName() + "."));
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
            bean.getAsDynamicObject().invokeMethod("failure");
            fail();
        } catch (Exception e) {
            assertThat(e, sameInstance((Exception) failure));
        }
    }

    @Test
    public void extraPropertiesAreInherited() {
        Bean bean = new Bean();
        bean.defineProperty("additional", "value");

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

        bean.defineProperty("additional", "value");
        assertTrue(inherited.hasProperty("additional"));
        assertThat(inherited.getProperty("additional"), equalTo((Object) "value"));
    }

    @Test
    public void additionalObjectPropertiesAreInherited() {
        Bean other = new Bean();
        other.defineProperty("other", "value");
        Bean bean = new Bean();
        bean.extensibleDynamicObject.addObject(other.getAsDynamicObject(), ExtensibleDynamicObject.Location.BeforeConvention);

        DynamicObject inherited = bean.getInheritable();
        assertTrue(inherited.hasProperty("other"));
        assertThat(inherited.getProperty("other"), equalTo((Object) "value"));
        assertThat(inherited.getProperties().get("other"), equalTo((Object) "value"));
    }

    @Test
    public void inheritedAdditionalObjectPropertiesTrackChanges() {
        Bean other = new Bean();
        other.defineProperty("other", "value");
        Bean bean = new Bean();

        DynamicObject inherited = bean.getInheritable();
        assertFalse(inherited.hasProperty("other"));

        bean.extensibleDynamicObject.addObject(other.getAsDynamicObject(), ExtensibleDynamicObject.Location.BeforeConvention);

        assertTrue(inherited.hasProperty("other"));
        assertThat(inherited.getProperty("other"), equalTo((Object) "value"));
    }

    @Test
    public void conventionPropertiesAreInherited() {
        Bean bean = new Bean();
        Convention convention = bean.extensibleDynamicObject.getConvention();
        ConventionBean conventionBean = new ConventionBean();
        conventionBean.setConventionProperty("value");
        convention.getPlugins().put("convention", conventionBean);

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

        Convention convention = bean.extensibleDynamicObject.getConvention();
        ConventionBean conventionBean = new ConventionBean();
        conventionBean.setConventionProperty("value");
        convention.getPlugins().put("convention", conventionBean);

        assertTrue(inherited.hasProperty("conventionProperty"));
        assertThat(inherited.getProperty("conventionProperty"), equalTo((Object) "value"));
    }

    @Test
    public void parentPropertiesAreInherited() {
        Bean parent = new Bean();
        parent.defineProperty("parentProperty", "value");
        Bean bean = new Bean();
        bean.setParent(parent.getAsDynamicObject());

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
        bean.defineProperty("additional", "value");

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
        Convention convention = bean.extensibleDynamicObject.getConvention();
        convention.getPlugins().put("convention", new ConventionBean());

        DynamicObject inherited = bean.getInheritable();
        assertTrue(inherited.hasMethod("conventionMethod", "a", "b"));
        assertThat(inherited.invokeMethod("conventionMethod", "a", "b"), equalTo((Object) "convention:a.b"));
    }

    @Test
    public void additionalObjectMethodsAreInherited() {
        Bean other = new Bean();
        Convention convention = other.extensibleDynamicObject.getConvention();
        convention.getPlugins().put("convention", new ConventionBean());

        Bean bean = new Bean();
        bean.extensibleDynamicObject.addObject(other.getAsDynamicObject(), ExtensibleDynamicObject.Location.BeforeConvention);

        DynamicObject inherited = bean.getInheritable();
        assertTrue(inherited.hasMethod("conventionMethod", "a", "b"));
        assertThat(inherited.invokeMethod("conventionMethod", "a", "b"), equalTo((Object) "convention:a.b"));
    }

    @Test
    public void parentMethodsAreInherited() {
        Bean parent = new Bean();
        Convention convention = parent.extensibleDynamicObject.getConvention();
        convention.getPlugins().put("convention", new ConventionBean());
        Bean bean = new Bean();
        bean.setParent(parent.getAsDynamicObject());

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

    @Test
    public void canGetObjectAsDynamicObject() {
        Bean bean = new Bean();
        assertThat(DynamicObjectUtil.asDynamicObject(bean), sameInstance(bean.getAsDynamicObject()));

        assertThat(DynamicObjectUtil.asDynamicObject(new Object()), instanceOf(DynamicObject.class));
    }

    @Test
    public void canCallGroovyDynamicMethods() {
        DynamicGroovyBean bean = new DynamicGroovyBean();
        DynamicObject object = new ExtensibleDynamicObject(bean, DynamicGroovyBean.class, TestUtil.instantiatorFactory().decorateLenient());
        Integer doubled = (Integer) object.invokeMethod("bar", 1);
        assertThat(doubled, equalTo(2));

        try {
            object.invokeMethod("xxx", 1, 2, 3);
            fail();
        } catch (MissingMethodException e) {
            assertThat(e.getMessage(), equalTo("Could not find method xxx() for arguments [1, 2, 3] on object of type " + DynamicGroovyBean.class.getName() + "."));
        }
    }

    public static class Bean extends GroovyObjectSupport implements DynamicObjectAware {
        private String readWriteProperty;
        private String _readOnlyProperty;
        private String writeOnlyProperty;
        private Integer differentTypesProperty;
        final ExtensibleDynamicObject extensibleDynamicObject;

        public Bean() {
            extensibleDynamicObject = new ExtensibleDynamicObject(this, Bean.class, TestUtil.instantiatorFactory().decorateLenient());
        }

        public DynamicObject getAsDynamicObject() {
            return extensibleDynamicObject;
        }

        @Override
        public String toString() {
            return "<bean>";
        }

        public void setParent(DynamicObject parent) {
            extensibleDynamicObject.setParent(parent);
        }

        public String getReadOnlyProperty() {
            return _readOnlyProperty;
        }

        public void doSetReadOnlyProperty(String readOnlyProperty) {
            this._readOnlyProperty = readOnlyProperty;
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
            return extensibleDynamicObject.getProperty(name);
        }

        public boolean hasProperty(String name) {
            return extensibleDynamicObject.hasProperty(name);
        }

        public void setProperty(String name, Object value) {
            extensibleDynamicObject.setProperty(name, value);
        }

        public Map<String, Object> getProperties() {
            return extensibleDynamicObject.getProperties();
        }

        public boolean hasMethod(String name, Object... arguments) {
            return extensibleDynamicObject.hasMethod(name, arguments);
        }

        public Object invokeMethod(String name, Object args) {
            return extensibleDynamicObject.invokeMethod(name, (args instanceof Object[]) ? (Object[]) args : new Object[]{args});
        }

        public DynamicObject getInheritable() {
            return extensibleDynamicObject.getInheritable();
        }

        public void defineProperty(String name, Object value) {
            extensibleDynamicObject.getConvention().getExtraProperties().set(name, value);
        }
    }

    private static class DynamicJavaBean extends GroovyBean {
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
