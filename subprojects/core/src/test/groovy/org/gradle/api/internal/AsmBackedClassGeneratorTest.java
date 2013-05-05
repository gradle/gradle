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
import org.gradle.api.Action;
import org.gradle.api.GradleException;
import org.gradle.api.JavaVersion;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.plugins.DslObject;
import org.gradle.api.plugins.Convention;
import org.gradle.api.plugins.ExtensionAware;
import org.gradle.api.plugins.ExtensionContainer;
import org.gradle.internal.reflect.ObjectInstantiationException;
import org.junit.Test;
import spock.lang.Issue;

import javax.inject.Inject;
import java.io.IOException;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.Callable;

import static org.gradle.util.HelperUtil.TEST_CLOSURE;
import static org.gradle.util.HelperUtil.call;
import static org.gradle.util.Matchers.isEmpty;
import static org.gradle.util.WrapUtil.toList;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

import static org.gradle.api.internal.AbstractClassGeneratorTestGroovy.*;

public class AsmBackedClassGeneratorTest {
    private final AbstractClassGenerator generator = new AsmBackedClassGenerator();

    @Test
    public void mixesInConventionAwareInterface() throws Exception {
        Class<? extends Bean> generatedClass = generator.generate(Bean.class);
        assertTrue(IConventionAware.class.isAssignableFrom(generatedClass));

        Bean bean = generatedClass.newInstance();

        IConventionAware conventionAware = (IConventionAware) bean;
        assertThat(conventionAware.getConventionMapping(), instanceOf(ConventionAwareHelper.class));
        conventionAware.getConventionMapping().map("prop", TEST_CLOSURE);
    }

    @Test
    public void mixesInDynamicObjectAwareInterface() throws Exception {
        Class<? extends Bean> generatedClass = generator.generate(Bean.class);
        assertTrue(DynamicObjectAware.class.isAssignableFrom(generatedClass));
        Bean bean = generatedClass.newInstance();
        DynamicObjectAware dynamicBean = (DynamicObjectAware) bean;

        dynamicBean.getAsDynamicObject().setProperty("prop", "value");
        assertThat(bean.getProp(), equalTo("value"));
        assertThat(bean.doStuff("some value"), equalTo("{some value}"));
    }

    @Test
    public void mixesInExtensionAwareInterface() throws Exception {
        Class<? extends Bean> generatedClass = generator.generate(Bean.class);
        assertTrue(ExtensionAware.class.isAssignableFrom(generatedClass));
        Bean bean = generatedClass.newInstance();
        ExtensionAware dynamicBean = (ExtensionAware) bean;

        assertThat(dynamicBean.getExtensions(), notNullValue());
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
    public void doesNotDecorateAlreadyDecoratedClass() {
        Class<? extends Bean> generatedClass = generator.generate(Bean.class);
        assertSame(generatedClass, generator.generate(generatedClass));
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
    public void includesGenericTypeInformationForOverriddenConstructor() throws Exception {
        Class<?> generatedClass = generator.generate(BeanWithComplexConstructor.class);
        Constructor<?> constructor = generatedClass.getDeclaredConstructors()[0];

        assertThat(constructor.getTypeParameters().length, equalTo(3));

        assertThat(constructor.getGenericParameterTypes().length, equalTo(12));

        // Callable
        Type paramType = constructor.getGenericParameterTypes()[0];
        assertThat(paramType, equalTo((Type) Callable.class));

        // Callable<String>
        paramType = constructor.getGenericParameterTypes()[1];
        assertThat(paramType, instanceOf(ParameterizedType.class));
        ParameterizedType parameterizedType = (ParameterizedType) paramType;
        assertThat(parameterizedType.getRawType(), equalTo((Type) Callable.class));
        assertThat(parameterizedType.getActualTypeArguments()[0], equalTo((Type) String.class));

        // Callable<? extends String>
        paramType = constructor.getGenericParameterTypes()[2];
        assertThat(paramType, instanceOf(ParameterizedType.class));
        parameterizedType = (ParameterizedType) paramType;
        assertThat(parameterizedType.getRawType(), equalTo((Type) Callable.class));
        assertThat(parameterizedType.getActualTypeArguments()[0], instanceOf(WildcardType.class));
        WildcardType wildcard = (WildcardType) parameterizedType.getActualTypeArguments()[0];
        assertThat(wildcard.getUpperBounds().length, equalTo(1));
        assertThat(wildcard.getUpperBounds()[0], equalTo((Type)String.class));
        assertThat(wildcard.getLowerBounds().length, equalTo(0));

        // Callable<? super String>
        paramType = constructor.getGenericParameterTypes()[3];
        assertThat(paramType, instanceOf(ParameterizedType.class));
        parameterizedType = (ParameterizedType) paramType;
        assertThat(parameterizedType.getRawType(), equalTo((Type) Callable.class));
        assertThat(parameterizedType.getActualTypeArguments()[0], instanceOf(WildcardType.class));
        wildcard = (WildcardType) parameterizedType.getActualTypeArguments()[0];
        assertThat(wildcard.getUpperBounds().length, equalTo(1));
        assertThat(wildcard.getUpperBounds()[0], equalTo((Type)Object.class));
        assertThat(wildcard.getLowerBounds().length, equalTo(1));
        assertThat(wildcard.getLowerBounds()[0], equalTo((Type)String.class));

        // Callable<?>
        paramType = constructor.getGenericParameterTypes()[4];
        assertThat(paramType, instanceOf(ParameterizedType.class));
        parameterizedType = (ParameterizedType) paramType;
        assertThat(parameterizedType.getRawType(), equalTo((Type) Callable.class));
        assertThat(parameterizedType.getActualTypeArguments()[0], instanceOf(WildcardType.class));
        wildcard = (WildcardType) parameterizedType.getActualTypeArguments()[0];
        assertThat(wildcard.getUpperBounds().length, equalTo(1));
        assertThat(wildcard.getUpperBounds()[0], equalTo((Type)Object.class));
        assertThat(wildcard.getLowerBounds().length, equalTo(0));

        // Callable<? extends Callable<?>>
        paramType = constructor.getGenericParameterTypes()[5];
        assertThat(paramType, instanceOf(ParameterizedType.class));
        parameterizedType = (ParameterizedType) paramType;
        assertThat(parameterizedType.getRawType(), equalTo((Type) Callable.class));
        assertThat(parameterizedType.getActualTypeArguments()[0], instanceOf(WildcardType.class));
        wildcard = (WildcardType) parameterizedType.getActualTypeArguments()[0];
        assertThat(wildcard.getUpperBounds().length, equalTo(1));
        assertThat(wildcard.getLowerBounds().length, equalTo(0));
        assertThat(wildcard.getUpperBounds()[0], instanceOf(ParameterizedType.class));
        parameterizedType = (ParameterizedType) wildcard.getUpperBounds()[0];
        assertThat(parameterizedType.getRawType(), equalTo((Type) Callable.class));
        assertThat(parameterizedType.getActualTypeArguments()[0], instanceOf(WildcardType.class));
        wildcard = (WildcardType) parameterizedType.getActualTypeArguments()[0];
        assertThat(wildcard.getUpperBounds().length, equalTo(1));
        assertThat(wildcard.getUpperBounds()[0], equalTo((Type) Object.class));
        assertThat(wildcard.getLowerBounds().length, equalTo(0));

        // Callable<S>
        paramType = constructor.getGenericParameterTypes()[6];
        assertThat(paramType, instanceOf(ParameterizedType.class));
        parameterizedType = (ParameterizedType) paramType;
        assertThat(parameterizedType.getRawType(), equalTo((Type) Callable.class));
        assertThat(parameterizedType.getActualTypeArguments()[0], instanceOf(TypeVariable.class));
        TypeVariable typeVariable = (TypeVariable) parameterizedType.getActualTypeArguments()[0];
        assertThat(typeVariable.getName(), equalTo("S"));
        assertThat(typeVariable.getBounds()[0], instanceOf(ParameterizedType.class));

        // Callable<? extends T>
        paramType = constructor.getGenericParameterTypes()[7];
        assertThat(paramType, instanceOf(ParameterizedType.class));
        parameterizedType = (ParameterizedType) paramType;
        assertThat(parameterizedType.getRawType(), equalTo((Type) Callable.class));
        assertThat(parameterizedType.getActualTypeArguments()[0], instanceOf(WildcardType.class));
        wildcard = (WildcardType) parameterizedType.getActualTypeArguments()[0];
        assertThat(wildcard.getUpperBounds().length, equalTo(1));
        assertThat(wildcard.getLowerBounds().length, equalTo(0));
        assertThat(wildcard.getUpperBounds()[0], instanceOf(TypeVariable.class));
        typeVariable = (TypeVariable) wildcard.getUpperBounds()[0];
        assertThat(typeVariable.getName(), equalTo("T"));
        assertThat(typeVariable.getBounds()[0], equalTo((Type) IOException.class));

        // V
        paramType = constructor.getGenericParameterTypes()[8];
        assertThat(paramType, instanceOf(TypeVariable.class));
        typeVariable = (TypeVariable) paramType;
        assertThat(typeVariable.getName(), equalTo("V"));
        assertThat(typeVariable.getBounds()[0], equalTo((Type) Object.class));

        GenericArrayType arrayType;

        // String[]
        paramType = constructor.getGenericParameterTypes()[9];

        /*
            Java 7 fixed http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=5041784
            The net effect is that non generic arrays are incorrectly encoded as generic
            array types pre Java 7 and correctly encoded as plain object arrays Java 7 and up.
         */
        if (JavaVersion.current().isJava7Compatible()) {
            assertThat(paramType, equalTo((Type) String[].class));
            assertThat(((Class<?>) paramType).getComponentType(), equalTo((Type) String.class));
        } else {
            assertThat(paramType, instanceOf(GenericArrayType.class));
            arrayType = (GenericArrayType) paramType;
            assertThat(arrayType.getGenericComponentType(), equalTo((Type) String.class));
        }

        // List<? extends String>[]
        paramType = constructor.getGenericParameterTypes()[10];
        assertThat(paramType, instanceOf(GenericArrayType.class));
        arrayType = (GenericArrayType) paramType;
        assertThat(arrayType.getGenericComponentType(), instanceOf(ParameterizedType.class));
        parameterizedType = (ParameterizedType) arrayType.getGenericComponentType();
        assertThat(parameterizedType.getRawType(), equalTo((Type) List.class));
        assertThat(parameterizedType.getActualTypeArguments().length, equalTo(1));
        assertThat(parameterizedType.getActualTypeArguments()[0], instanceOf(WildcardType.class));

        // boolean
        paramType = constructor.getGenericParameterTypes()[11];
        assertThat(paramType, equalTo((Type) Boolean.TYPE));

        assertThat(constructor.getGenericExceptionTypes().length, equalTo(2));

        // throws Exception
        Type exceptionType = constructor.getGenericExceptionTypes()[0];
        assertThat(exceptionType, equalTo((Type) Exception.class));

        // throws T
        exceptionType = constructor.getGenericExceptionTypes()[1];
        assertThat(exceptionType, instanceOf(TypeVariable.class));
        typeVariable = (TypeVariable) exceptionType;
        assertThat(typeVariable.getName(), equalTo("T"));
    }

    @Test
    public void includesAnnotationInformationForOverriddenConstructor() throws Exception {
        Class<?> generatedClass = generator.generate(BeanWithAnnotatedConstructor.class);
        Constructor<?> constructor = generatedClass.getDeclaredConstructors()[0];

        assertThat(constructor.getAnnotation(Inject.class), notNullValue());
    }

    @Test
    public void canConstructInstance() throws Exception {
        Bean bean = generator.newInstance(BeanWithConstructor.class, "value");
        assertThat(bean.getClass(), sameInstance((Object) generator.generate(BeanWithConstructor.class)));
        assertThat(bean.getProp(), equalTo("value"));

        bean = generator.newInstance(BeanWithConstructor.class);
        assertThat(bean.getProp(), equalTo("default value"));

        bean = generator.newInstance(BeanWithConstructor.class, 127);
        assertThat(bean.getProp(), equalTo("127"));
    }

    @Test
    public void reportsConstructionFailure() {
        try {
            generator.newInstance(UnconstructableBean.class);
            fail();
        } catch (ObjectInstantiationException e) {
            assertThat(e.getCause(), sameInstance(UnconstructableBean.failure));
        }

        try {
            generator.newInstance(Bean.class, "arg1", 2);
            fail();
        } catch (ObjectInstantiationException e) {
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

        conventionAware.getConventionMapping().map("prop", new Callable<String>() {
            public String call() {
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
    @Issue("GRADLE-2163")
    public void appliesConventionMappingToGroovyBoolean() throws Exception {
        BeanWithGroovyBoolean bean = generator.generate(BeanWithGroovyBoolean.class).newInstance();

        assertTrue(bean instanceof IConventionAware);
        assertThat(bean.getSmallB(), equalTo(false));
        assertThat(bean.getBigB(), nullValue());

        IConventionAware conventionAware = (IConventionAware) bean;

        conventionAware.getConventionMapping().map("smallB", new Callable<Object>() {
            public Object call() throws Exception {
                return true;
            }
        });

        assertThat(bean.isSmallB(), equalTo(true));
        assertThat(bean.getSmallB(), equalTo(true));

        bean.setSmallB(false);
        assertThat(bean.isSmallB(), equalTo(false));
        assertThat(bean.getSmallB(), equalTo(false));

        conventionAware.getConventionMapping().map("bigB", new Callable<Object>() {
            public Object call() throws Exception {
                return Boolean.TRUE;
            }
        });

        assertThat(bean.getBigB(), equalTo(Boolean.TRUE));
        bean.setBigB(Boolean.FALSE);
        assertThat(bean.getBigB(), equalTo(Boolean.FALSE));
    }

    @Test
    public void appliesConventionMappingToCollectionGetter() throws Exception {
        Class<? extends CollectionBean> generatedClass = generator.generate(CollectionBean.class);
        CollectionBean bean = generatedClass.newInstance();
        IConventionAware conventionAware = (IConventionAware) bean;
        final List<String> conventionValue = toList("value");

        assertThat(bean.getProp(), isEmpty());

        conventionAware.getConventionMapping().map("prop", new Callable<Object>() {
            public Object call() {
                return conventionValue;
            }
        });

        assertThat(bean.getProp(), sameInstance(conventionValue));

        bean.setProp(toList("other"));
        assertThat(bean.getProp(), equalTo(toList("other")));

        bean.setProp(Collections.<String>emptyList());
        assertThat(bean.getProp(), equalTo(Collections.<String>emptyList()));

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

        IConventionAware conventionAware = (IConventionAware) bean;
        conventionAware.getConventionMapping().map("booleanProperty", new Callable<Object>() {
            public Object call() throws Exception {
                return true;
            }
        });

        assertThat(bean.getBooleanProperty(), equalTo(true));

        bean.setBooleanProperty(false);
        assertThat(bean.getBooleanProperty(), equalTo(false));
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
        conventionAware.getConventionMapping().map("property", new Callable<Object>() {
            public Object call() throws Exception {
                throw new UnsupportedOperationException();
            }
        });
        conventionAware.getConventionMapping().map("interfaceProperty", new Callable<Object>() {
            public Object call() throws Exception {
                throw new UnsupportedOperationException();
            }
        });
        conventionAware.getConventionMapping().map("overriddenProperty", new Callable<Object>() {
            public Object call() throws Exception {
                return "conventionValue";
            }
        });
        conventionAware.getConventionMapping().map("otherProperty", new Callable<Object>() {
            public Object call() throws Exception {
                return "conventionValue";
            }
        });
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
        assertThat(bean.getAsDynamicObject(), sameInstance(bean.conv.getExtensionsAsDynamicObject()));
    }

    @Test
    public void canAddDynamicPropertiesAndMethodsToJavaObject() throws Exception {
        Bean bean = generator.generate(Bean.class).newInstance();
        DynamicObjectAware dynamicObjectAware = (DynamicObjectAware) bean;
        ConventionObject conventionObject = new ConventionObject();
        new DslObject(dynamicObjectAware).getConvention().getPlugins().put("plugin", conventionObject);

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
        new DslObject(dynamicObjectAware).getConvention().getPlugins().put("plugin", conventionObject);

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
    public void usesExistingGetAsDynamicObjectMethod() throws Exception {
        DynamicObjectBean bean = generator.generate(DynamicObjectBean.class).newInstance();

        call("{ it.prop = 'value' }", bean);
        assertThat(call("{ it.prop }", bean), equalTo((Object) "value"));

        bean.getAsDynamicObject().setProperty("prop", "value2");
        assertThat(call("{ it.prop }", bean), equalTo((Object) "value2"));

        bean.getAsDynamicObject().setProperty("dynamicProp", "value");
        assertThat(call("{ it.dynamicProp }", bean), equalTo((Object) "value"));
    }

    @Test
    public void constructorCanCallGetter() throws Exception {
        BeanUsesPropertiesInConstructor bean = generator.newInstance(BeanUsesPropertiesInConstructor.class);

        assertThat(bean.name, equalTo("default-name"));
    }

    @Test
    public void mixesInSetValueMethodForSingleValuedProperty() throws Exception {
        BeanWithVariousGettersAndSetters bean = generator.generate(BeanWithVariousGettersAndSetters.class).newInstance();

        call("{ it.prop 'value'}", bean);
        assertThat(bean.getProp(), equalTo("value"));

        call("{ it.finalGetter 'another'}", bean);
        assertThat(bean.getFinalGetter(), equalTo("another"));

        call("{ it.writeOnly 12}", bean);
        assertThat(bean.writeOnly, equalTo(12));

        call("{ it.primitive 12}", bean);
        assertThat(bean.getPrimitive(), equalTo(12));
    }

    @Test
    public void doesNotUseConventionValueOnceSetValueMethodHasBeenCalled() throws Exception {
        Bean bean = generator.generate(Bean.class).newInstance();
        IConventionAware conventionAware = (IConventionAware) bean;
        conventionAware.getConventionMapping().map("prop", new Callable<Object>() {
            public Object call() throws Exception {
                return "[default]";
            }
        });

        assertThat(bean.getProp(), equalTo("[default]"));

        call("{ it.prop 'value'}", bean);
        assertThat(bean.getProp(), equalTo("value"));
    }

    @Test
    public void doesNotMixInSetValueMethodForReadOnlyProperty() throws Exception {
        BeanWithReadOnlyProperties bean = generator.generate(BeanWithReadOnlyProperties.class).newInstance();

        try {
            call("{ it.prop 'value'}", bean);
            fail();
        } catch (MissingMethodException e) {
            assertThat(e.getMethod(), equalTo("prop"));
        }
    }

    @Test
    public void doesNotMixInSetValueMethodForMultiValueProperty() throws Exception {
        CollectionBean bean = generator.generate(CollectionBean.class).newInstance();

        try {
            call("{ def val = ['value']; it.prop val}", bean);
            fail();
        } catch (MissingMethodException e) {
            assertThat(e.getMethod(), equalTo("prop"));
        }
    }

    @Test
    public void overridesExistingSetValueMethod() throws Exception {
        BeanWithDslMethods bean = generator.generate(BeanWithDslMethods.class).newInstance();
        IConventionAware conventionAware = (IConventionAware) bean;
        conventionAware.getConventionMapping().map("prop", new Callable<Object>() {
            public Object call() throws Exception {
                return "[default]";
            }
        });

        assertThat(bean.getProp(), equalTo("[default]"));

        assertThat(call("{ it.prop 'value'}", bean), sameInstance((Object) bean));
        assertThat(bean.getProp(), equalTo("[value]"));

        assertThat(call("{ it.prop 1.2}", bean), sameInstance((Object) bean));
        assertThat(bean.getProp(), equalTo("<1.2>"));

        assertThat(call("{ it.prop 1}", bean), nullValue());
        assertThat(bean.getProp(), equalTo("<1>"));

        // failing, seems to be that set method override doesn't work for iterables - GRADLE-2097
        //assertThat(call("{ bean, list -> bean.things(list) }", bean, new LinkedList<Object>()), nullValue());
        //assertThat(bean.getThings().size(), equalTo(0));

        //assertThat(call("{ bean -> bean.things([1,2,3]) }", bean), nullValue());
        //assertThat(bean.getThings().size(), equalTo(3));

        //FileCollection files = ProjectBuilder.builder().build().files();
        //assertThat(call("{ bean, fc -> bean.files fc}", bean, files), nullValue());
        //assertThat(bean.getFiles(), sameInstance(files));
    }

    @Test
    public void mixesInClosureOverloadForActionMethod() throws Exception {
        Bean bean = generator.generate(Bean.class).newInstance();
        bean.prop = "value";

        call("{def value; it.doStuff { value = it }; assert value == \'value\' }", bean);
    }

    @Test
    public void doesNotOverrideExistingClosureOverload() throws IllegalAccessException, InstantiationException {
        BeanWithDslMethods bean = generator.generate(BeanWithDslMethods.class).newInstance();
        bean.prop = "value";

        assertThat(call("{def value; it.doStuff { value = it }; return value }", bean), equalTo((Object) "[value]"));
    }

    @Test public void generatesDslObjectCompatibleObject() throws Exception {
        new DslObject(generator.generate(Bean.class).newInstance());
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

        public void doStuff(Action<String> action) {
            action.execute(getProp());
        }
    }

    public static class BeanWithReadOnlyProperties {
        public String getProp() {
            return "value";
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

        public BeanWithConstructor(int value) {
            setProp(String.valueOf(value));
        }
    }

    public static class BeanWithComplexConstructor {
        public <T extends IOException, S extends Callable<String>, V> BeanWithComplexConstructor(
                Callable rawValue,
                Callable<String> value,
                Callable<? extends String> subType,
                Callable<? super String> superType,
                Callable<?> wildcard,
                Callable<? extends Callable<?>> nested,
                Callable<S> typeVar,
                Callable<? extends T> typeVarWithBounds,
                V genericVar,
                String[] array,
                List<? extends String>[] genericArray,
                boolean primitive
        ) throws Exception, T {
        }
    }

    public static class BeanWithAnnotatedConstructor {
        @Inject
        public BeanWithAnnotatedConstructor() {
        }
    }

    public static class BeanWithDslMethods extends Bean {
        private String prop;
        private FileCollection files;
        private List<Object> things;

        public String getProp() {
            return prop;
        }

        public void setProp(String prop) {
            this.prop = prop;
        }

        public FileCollection getFiles() {
            return files;
        }

        public void setFiles(FileCollection files) {
            this.files = files;
        }

        public List<Object> getThings() {
            return things;
        }

        public void setThings(List<Object> things) {
            this.things = things;
        }

        public BeanWithDslMethods prop(String property) {
            this.prop = String.format("[%s]", property);
            return this;
        }

        public BeanWithDslMethods prop(Object property) {
            this.prop = String.format("<%s>", property);
            return this;
        }

        public void prop(int property) {
            this.prop = String.format("<%s>", property);
        }

        public void doStuff(Closure cl) {
            cl.call(String.format("[%s]", getProp()));
        }
    }

    public static class ConventionAwareBean extends Bean implements IConventionAware, ConventionMapping {
        public Convention getConvention() {
            throw new UnsupportedOperationException();
        }

        public void setConvention(Convention convention) {
            throw new UnsupportedOperationException();
        }

        public MappedProperty map(String propertyName, Closure value) {
            throw new UnsupportedOperationException();
        }

        public MappedProperty map(String propertyName, Callable<?> value) {
            throw new UnsupportedOperationException();
        }

        public <T> T getConventionValue(T actualValue, String propertyName) {
            if (actualValue instanceof String) {
                return (T) ("[" + actualValue + "]");
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
        Convention conv = new ExtensibleDynamicObject(this, ThreadGlobalInstantiator.getOrCreate()).getConvention();

        public Convention getConvention() {
            return conv;
        }

        public ExtensionContainer getExtensions() {
            return conv;
        }

        public DynamicObject getAsDynamicObject() {
            return conv.getExtensionsAsDynamicObject();
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
        private boolean b;

        public String[] getArrayProperty() {
            return new String[1];
        }

        public boolean getBooleanProperty() {
            return b;
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

        public void setBooleanProperty(boolean b) {
            this.b = b;
        }
    }

    public static class BeanWithVariousGettersAndSetters extends Bean {
        private int primitive;
        private boolean bool;
        private String finalGetter;
        private Integer writeOnly;

        public int getPrimitive() {
            return primitive;
        }

        public void setPrimitive(int primitive) {
            this.primitive = primitive;
        }

        public final String getFinalGetter() {
            return finalGetter;
        }

        public void setFinalGetter(String value) {
            finalGetter = value;
        }

        public void setWriteOnly(Integer value) {
            writeOnly = value;
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

    public static class BeanUsesPropertiesInConstructor {
        final String name;

        public BeanUsesPropertiesInConstructor() {
            name = getName();
        }

        public String getName() {
            return "default-name";
        }
    }

    public static class UnconstructableBean {
        static Throwable failure = new UnsupportedOperationException();

        public UnconstructableBean() throws Throwable {
            throw failure;
        }
    }

    public static abstract class AbstractBean {
        abstract void implementMe();
    }

    private static class PrivateBean {
    }
}
