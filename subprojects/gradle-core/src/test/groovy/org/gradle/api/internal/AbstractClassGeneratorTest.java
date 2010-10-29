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
package org.gradle.api.internal;

import groovy.lang.Closure;
import groovy.lang.GroovyObject;
import groovy.lang.GroovyRuntimeException;
import org.gradle.api.GradleException;
import org.gradle.api.internal.plugins.DefaultConvention;
import org.gradle.api.plugins.Convention;
import org.gradle.api.tasks.ConventionValue;
import org.gradle.util.GUtil;
import org.junit.Before;
import org.junit.Test;

import java.util.*;
import java.util.concurrent.Callable;

import static org.gradle.util.HelperUtil.TEST_CLOSURE;
import static org.gradle.util.HelperUtil.call;
import static org.gradle.util.Matchers.isEmpty;
import static org.gradle.util.WrapUtil.toList;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

public abstract class AbstractClassGeneratorTest {
    private ClassGenerator generator;

    @Before
    public void setUp() {
        generator = createGenerator();
    }

    protected abstract ClassGenerator createGenerator();

    @Test
    public void mixesInConventionAwareInterface() throws Exception {
        Class<? extends Bean> generatedClass = generator.generate(Bean.class);
        assertTrue(IConventionAware.class.isAssignableFrom(generatedClass));

        Bean bean = generatedClass.newInstance();

        IConventionAware conventionAware = (IConventionAware) bean;
        assertThat(conventionAware.getConventionMapping(), instanceOf(ConventionAwareHelper.class));
        conventionAware.getConventionMapping().map("prop", TEST_CLOSURE);
        ConventionMapping mapping = new ConventionAwareBean();
        conventionAware.setConventionMapping(mapping);
        assertThat(conventionAware.getConventionMapping(), sameInstance(mapping));
    }

    @Test
    public void mixesInDynamicObjectAwareInterface() throws Exception {
        Class<? extends Bean> generatedClass = generator.generate(Bean.class);
        assertTrue(DynamicObjectAware.class.isAssignableFrom(generatedClass));
        Bean bean = generatedClass.newInstance();
        ((DynamicObjectAware) bean).getAsDynamicObject().setProperty("prop", "value");
        assertThat(bean.getProp(), equalTo("value"));
        assertThat(bean.doStuff("some value"), equalTo("{some value}"));
    }

    @Test
    public void mixesInGroovyObjectInterface() throws Exception {
        Class<? extends Bean> generatedClass = generator.generate(Bean.class);
        assertTrue(GroovyObject.class.isAssignableFrom(generatedClass));
        Bean bean = generatedClass.newInstance();
        GroovyObject groovyObject = (GroovyObject) bean;
        assertThat(groovyObject.getMetaClass(), notNullValue());

        groovyObject.setProperty("prop", "value");
        assertThat(bean.getProp(), equalTo("value"));
        assertThat(groovyObject.getProperty("prop"), equalTo((Object) "value"));
        assertThat(groovyObject.invokeMethod("doStuff", new Object[]{"some value"}), equalTo((Object) "{some value}"));
    }

    @Test
    public void cachesGeneratedSubclass() {
        assertSame(generator.generate(Bean.class), generator.generate(Bean.class));
    }

    @Test
    public void overridesPublicConstructors() throws Exception {
        Class<? extends Bean> generatedClass = generator.generate(BeanWithConstructor.class);
        Bean bean = generatedClass.getConstructor(String.class).newInstance("value");
        assertThat(bean.getProp(), equalTo("value"));

        bean = generatedClass.getConstructor().newInstance();
        assertThat(bean.getProp(), equalTo("default value"));
    }

    @Test
    public void canConstructInstance() throws Exception {
        Bean bean = generator.newInstance(BeanWithConstructor.class, "value");
        assertThat(bean.getClass(), sameInstance((Object) generator.generate(BeanWithConstructor.class)));
        assertThat(bean.getProp(), equalTo("value"));

        bean = generator.newInstance(BeanWithConstructor.class);
        assertThat(bean.getProp(), equalTo("default value"));
    }

    @Test
    public void reportsConstructionFailure() {
        try {
            generator.newInstance(UnconstructableBean.class);
            fail();
        } catch (UnsupportedOperationException e) {
            assertThat(e, sameInstance(UnconstructableBean.failure));
        }

        try {
            generator.newInstance(Bean.class, "arg1", 2);
            fail();
        } catch (GroovyRuntimeException e) {
            // expected
        }

        try {
            generator.newInstance(AbstractBean.class);
            fail();
        } catch (GradleException e) {
            assertThat(e.getMessage(), equalTo("Cannot create a proxy class for abstract class 'AbstractBean'."));
        }

        try {
            generator.newInstance(PrivateBean.class);
            fail();
        } catch (GradleException e) {
            assertThat(e.getMessage(), equalTo("Cannot create a proxy class for private class 'PrivateBean'."));
        }
    }

    @Test
    public void appliesConventionMappingToEachGetter() throws Exception {
        Class<? extends Bean> generatedClass = generator.generate(Bean.class);
        assertTrue(IConventionAware.class.isAssignableFrom(generatedClass));
        Bean bean = generatedClass.newInstance();
        IConventionAware conventionAware = (IConventionAware) bean;

        assertThat(bean.getProp(), nullValue());

        conventionAware.getConventionMapping().map("prop", new ConventionValue() {
            public Object getValue(Convention convention, IConventionAware conventionAwareObject) {
                return "conventionValue";
            }
        });

        assertThat(bean.getProp(), equalTo("conventionValue"));

        bean.setProp("value");
        assertThat(bean.getProp(), equalTo("value"));

        bean.setProp(null);
        assertThat(bean.getProp(), nullValue());
    }

    @Test
    public void appliesConventionMappingToCollectionGetter() throws Exception {
        Class<? extends CollectionBean> generatedClass = generator.generate(CollectionBean.class);
        CollectionBean bean = generatedClass.newInstance();
        IConventionAware conventionAware = (IConventionAware) bean;
        final List<String> conventionValue = toList("value");

        assertThat(bean.getProp(), isEmpty());

        conventionAware.getConventionMapping().map("prop", new ConventionValue() {
            public Object getValue(Convention convention, IConventionAware conventionAwareObject) {
                return conventionValue;
            }
        });

        assertThat(bean.getProp(), sameInstance(conventionValue));

        bean.setProp(toList("other"));
        assertThat(bean.getProp(), equalTo(toList("other")));

        bean.setProp(Collections.EMPTY_LIST);
        assertThat(bean.getProp(), equalTo(Collections.EMPTY_LIST));

        bean.setProp(null);
        assertThat(bean.getProp(), nullValue());
    }

    @Test
    public void handlesVariousPropertyTypes() throws Exception {
        BeanWithVariousPropertyTypes bean = generator.generate(BeanWithVariousPropertyTypes.class).newInstance();

        assertThat(bean.getArrayProperty(), notNullValue());
        assertThat(bean.getBooleanProperty(), equalTo(false));
        assertThat(bean.getLongProperty(), equalTo(12L));
        assertThat(bean.setReturnValueProperty("p"), sameInstance(bean));
    }

    @Test
    public void doesNotOverrideMethodsFromConventionAwareInterface() throws Exception {
        Class<? extends ConventionAwareBean> generatedClass = generator.generate(ConventionAwareBean.class);
        assertTrue(IConventionAware.class.isAssignableFrom(generatedClass));
        ConventionAwareBean bean = generatedClass.newInstance();
        assertSame(bean, bean.getConventionMapping());

        bean.setProp("value");
        assertEquals("[value]", bean.getProp());
    }

    @Test
    public void doesNotOverrideMethodsFromSuperclassesMarkedWithAnnotation() throws Exception {
        BeanSubClass bean = generator.generate(BeanSubClass.class).newInstance();
        IConventionAware conventionAware = (IConventionAware) bean;
        conventionAware.getConventionMapping().map(GUtil.map(
                "property", new ConventionValue(){
                    public Object getValue(Convention convention, IConventionAware conventionAwareObject) {
                        throw new UnsupportedOperationException();
                    }
                },
                "interfaceProperty", new ConventionValue(){
                    public Object getValue(Convention convention, IConventionAware conventionAwareObject) {
                        throw new UnsupportedOperationException();
                    }
                },
                "overriddenProperty", new ConventionValue(){
                    public Object getValue(Convention convention, IConventionAware conventionAwareObject) {
                        return "conventionValue";
                    }
                },
                "otherProperty", new ConventionValue(){
                    public Object getValue(Convention convention, IConventionAware conventionAwareObject) {
                        return "conventionValue";
                    }
                }));
        assertEquals(null, bean.getProperty());
        assertEquals(null, bean.getInterfaceProperty());
        assertEquals("conventionValue", bean.getOverriddenProperty());
        assertEquals("conventionValue", bean.getOtherProperty());
    }

    @Test
    public void doesNotMixInConventionMappingToClassWithAnnotation() throws Exception {
        NoMappingBean bean = generator.generate(NoMappingBean.class).newInstance();
        assertFalse(bean instanceof IConventionAware);
        assertNull(bean.getInterfaceProperty());

        // Check dynamic object behaviour still works
        assertTrue(bean instanceof DynamicObjectAware);
    }

    @Test
    public void doesNotOverrideMethodsFromDynamicObjectAwareInterface() throws Exception {
        DynamicObjectAwareBean bean = generator.generate(DynamicObjectAwareBean.class).newInstance();
        assertThat(bean.getConvention(), sameInstance(bean.conv));
        Convention newConvention = new DefaultConvention();
        bean.setConvention(newConvention);
        assertThat(bean.getConvention(), sameInstance(newConvention));
        assertThat(bean.getAsDynamicObject(), sameInstance((DynamicObject) newConvention));
    }

    @Test
    public void doesNotMixInDynamicObjectToClassWithAnnotation() throws Exception {
        Class<? extends NoDynamicBean> generatedType = generator.generate(NoDynamicBean.class);
        assertFalse(DynamicObjectAware.class.isAssignableFrom(generatedType));

        // Check convention mapping still works
        assertTrue(IConventionAware.class.isAssignableFrom(generatedType));
        NoDynamicBean bean = generatedType.newInstance();

        // Check MOP methods not overridden
        bean.setProp("value");
        assertThat(call("{ it.prop }", bean), equalTo((Object) "value"));
        assertThat(call("{ it.dynamicProp }", bean), equalTo((Object) "[dynamicProp]"));
    }

    @Test
    public void usesSameConventionForDynamicObjectAndConventionMappings() throws Exception {
        Bean bean = generator.generate(Bean.class).newInstance();
        IConventionAware conventionAware = (IConventionAware) bean;
        DynamicObjectAware dynamicObjectAware = (DynamicObjectAware) bean;
        assertThat(dynamicObjectAware.getConvention(), sameInstance(conventionAware.getConventionMapping().getConvention()));

        Convention newConvention = new DefaultConvention();
        dynamicObjectAware.setConvention(newConvention);
        assertThat(dynamicObjectAware.getConvention(), sameInstance(newConvention));
        assertThat(conventionAware.getConventionMapping().getConvention(), sameInstance(newConvention));
    }

    @Test
    public void canAddDynamicPropertiesAndMethodsToJavaObject() throws Exception {
        Bean bean = generator.generate(Bean.class).newInstance();
        DynamicObjectAware dynamicObjectAware = (DynamicObjectAware) bean;
        ConventionObject conventionObject = new ConventionObject();
        dynamicObjectAware.getConvention().getPlugins().put("plugin", conventionObject);

        call("{ it.conventionProperty = 'value' }", bean);
        assertThat(conventionObject.getConventionProperty(), equalTo("value"));
        assertThat(call("{ it.hasProperty('conventionProperty') }", bean), notNullValue());
        assertThat(call("{ it.conventionProperty }", bean), equalTo((Object) "value"));
        assertThat(call("{ it.conventionMethod('value') }", bean), equalTo((Object) "[value]"));
        assertThat(call("{ it.invokeMethod('conventionMethod', 'value') }", bean), equalTo((Object) "[value]"));
    }

    @Test
    public void canAddDynamicPropertiesAndMethodsToGroovyObject() throws Exception {
        TestDecoratedGroovyBean bean = generator.generate(TestDecoratedGroovyBean.class).newInstance();
        DynamicObjectAware dynamicObjectAware = (DynamicObjectAware) bean;
        ConventionObject conventionObject = new ConventionObject();
        dynamicObjectAware.getConvention().getPlugins().put("plugin", conventionObject);

        call("{ it.conventionProperty = 'value' }", bean);
        assertThat(conventionObject.getConventionProperty(), equalTo("value"));
        assertThat(call("{ it.hasProperty('conventionProperty') }", bean), notNullValue());
        assertThat(call("{ it.conventionProperty }", bean), equalTo((Object) "value"));
        assertThat(call("{ it.conventionMethod('value') }", bean), equalTo((Object) "[value]"));
        assertThat(call("{ it.invokeMethod('conventionMethod', 'value') }", bean), equalTo((Object) "[value]"));
    }

    @Test
    public void respectsPropertiesAddedToMetaClassOfJavaObject() throws Exception {
        Bean bean = generator.generate(Bean.class).newInstance();

        call("{ it.metaClass.getConventionProperty = { -> 'value'} }", bean);
        assertThat(call("{ it.hasProperty('conventionProperty') }", bean), notNullValue());
        assertThat(call("{ it.getConventionProperty() }", bean), equalTo((Object) "value"));
        assertThat(call("{ it.conventionProperty }", bean), equalTo((Object) "value"));
    }

    @Test
    public void respectsPropertiesAddedToMetaClassOfGroovyObject() throws Exception {
        TestDecoratedGroovyBean bean = generator.generate(TestDecoratedGroovyBean.class).newInstance();

        call("{ it.metaClass.getConventionProperty = { -> 'value'} }", bean);
        assertThat(call("{ it.hasProperty('conventionProperty') }", bean), notNullValue());
        assertThat(call("{ it.getConventionProperty() }", bean), equalTo((Object) "value"));
        assertThat(call("{ it.conventionProperty }", bean), equalTo((Object) "value"));
    }

    @Test
    public void usesExistingDynamicObject() throws Exception {
        DynamicObjectBean bean = generator.generate(DynamicObjectBean.class).newInstance();

        call("{ it.prop = 'value' }", bean);
        assertThat(call("{ it.prop }", bean), equalTo((Object) "value"));

        bean.getAsDynamicObject().setProperty("prop", "value2");
        assertThat(call("{ it.prop }", bean), equalTo((Object) "value2"));

        bean.getAsDynamicObject().setProperty("dynamicProp", "value");
        assertThat(call("{ it.dynamicProp }", bean), equalTo((Object) "value"));
    }

    public static class Bean {
        private String prop;

        public String getProp() {
            return prop;
        }

        public void setProp(String prop) {
            this.prop = prop;
        }

        public String doStuff(String value) {
            return "{" + value + "}";
        }
    }

    public static class CollectionBean {
        private List<String> prop = new ArrayList<String>();

        public List<String> getProp() {
            return prop;
        }

        public void setProp(List<String> prop) {
            this.prop = prop;
        }
    }

    public static class BeanWithConstructor extends Bean {
        public BeanWithConstructor() {
            this("default value");
        }

        public BeanWithConstructor(String value) {
            setProp(value);
        }
    }

    public static class ConventionAwareBean extends Bean implements IConventionAware, ConventionMapping {
        Map<String, ConventionValue> mapping = new HashMap<String, ConventionValue>();

        public Convention getConvention() {
            throw new UnsupportedOperationException();
        }

        public void setConvention(Convention convention) {
            throw new UnsupportedOperationException();
        }

        public ConventionMapping map(Map<String, ? extends ConventionValue> properties) {
            throw new UnsupportedOperationException();
        }

        public MappedProperty map(String propertyName, Closure value) {
            throw new UnsupportedOperationException();
        }

        public MappedProperty map(String propertyName, ConventionValue value) {
            throw new UnsupportedOperationException();
        }

        public MappedProperty map(String propertyName, Callable<?> value) {
            throw new UnsupportedOperationException();
        }

        public <T> T getConventionValue(T actualValue, String propertyName) {
            if (actualValue instanceof String) {
                return (T)("[" + actualValue + "]");
            } else {
                throw new UnsupportedOperationException();
            }
        }

        public <T> T getConventionValue(T actualValue, String propertyName, boolean isExplicitValue) {
            return getConventionValue(actualValue, propertyName);
        }

        public ConventionMapping getConventionMapping() {
            return this;
        }

        public void setConventionMapping(ConventionMapping conventionMapping) {
            throw new UnsupportedOperationException();
        }
    }

    public static class DynamicObjectAwareBean extends Bean implements DynamicObjectAware {
        Convention conv = new DefaultConvention();

        public Convention getConvention() {
            return conv;
        }

        public void setConvention(Convention convention) {
            this.conv = convention;
        }

        public DynamicObject getAsDynamicObject() {
            return conv;
        }
    }

    public static class ConventionObject {
        private String conventionProperty;

        public String getConventionProperty() {
            return conventionProperty;
        }

        public void setConventionProperty(String conventionProperty) {
            this.conventionProperty = conventionProperty;
        }

        public Object conventionMethod(String value) {
            return "[" + value + "]";
        }
    }

    public static class BeanWithVariousPropertyTypes {
        public String[] getArrayProperty() {
            return new String[1];
        }

        public boolean getBooleanProperty() {
            return false;
        }

        public long getLongProperty() {
            return 12L;
        }

        public String getReturnValueProperty() {
            return "value";
        }
        
        public BeanWithVariousPropertyTypes setReturnValueProperty(String val) {
            return this;
        }
    }

    public interface SomeType {
        String getInterfaceProperty();
    }

    @NoConventionMapping
    public static class NoMappingBean implements SomeType {
        public String getProperty() {
            return null;
        }

        public String getInterfaceProperty() {
            return null;
        }

        public String getOverriddenProperty() {
            return null;
        }
    }

    @NoDynamicObject
    public static class NoDynamicBean extends Bean {
        Object propertyMissing(String name) {
            return "[" + name + "]";
        }
    }

    public static class DynamicObjectBean {
        private final BeanDynamicObject dynamicObject = new BeanDynamicObject(new Bean());

        public DynamicObject getAsDynamicObject() {
            return dynamicObject;
        }
    }

    public static class BeanSubClass extends NoMappingBean {
        @Override
        public String getOverriddenProperty() {
            return null;
        }

        public String getOtherProperty() {
            return null;
        }
    }

    public static class UnconstructableBean {
        static UnsupportedOperationException failure = new UnsupportedOperationException();

        public UnconstructableBean() {
            throw failure;
        }
    }

    public static abstract class AbstractBean {
        abstract void implementMe();
    }

    private static class PrivateBean {}
}
