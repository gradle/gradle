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
import groovy.lang.MissingMethodException;
import org.gradle.api.Action;
import org.gradle.api.GradleException;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.plugins.DslObject;
import org.gradle.api.internal.provider.DefaultProviderFactory;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.plugins.Convention;
import org.gradle.api.plugins.ExtensionAware;
import org.gradle.api.plugins.ExtensionContainer;
import org.gradle.api.provider.Property;
import org.gradle.api.reflect.HasPublicType;
import org.gradle.api.reflect.ObjectInstantiationException;
import org.gradle.api.reflect.TypeOf;
import org.gradle.internal.metaobject.BeanDynamicObject;
import org.gradle.internal.metaobject.DynamicObject;
import org.gradle.internal.reflect.DirectInstantiator;
import org.gradle.internal.reflect.JavaReflectionUtil;
import org.gradle.util.TestUtil;
import org.junit.Test;
import spock.lang.Issue;

import javax.inject.Inject;
import java.io.IOException;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Constructor;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;

import static org.gradle.api.internal.AbstractClassGeneratorTestGroovy.BeanWithGroovyBoolean;
import static org.gradle.api.reflect.TypeOf.typeOf;
import static org.gradle.util.Matchers.isEmpty;
import static org.gradle.util.TestUtil.TEST_CLOSURE;
import static org.gradle.util.TestUtil.call;
import static org.gradle.util.WrapUtil.toList;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class AsmBackedClassGeneratorTest {
    private final AbstractClassGenerator generator = new AsmBackedClassGenerator();

    @Test
    public void mixesInGeneratedSubclassInterface() throws Exception {
        Class<? extends Bean> generatedClass = generator.generate(Bean.class);
        assertTrue(GeneratedSubclasses.is(generatedClass));
        assertEquals(Bean.class, GeneratedSubclasses.unpack(generatedClass));
    }

    @Test
    public void mixesInConventionAwareInterface() throws Exception {
        Class<? extends Bean> generatedClass = generator.generate(Bean.class);
        assertTrue(IConventionAware.class.isAssignableFrom(generatedClass));

        Bean bean = JavaReflectionUtil.newInstance(generatedClass);

        IConventionAware conventionAware = (IConventionAware) bean;
        assertThat(conventionAware.getConventionMapping(), instanceOf(ConventionAwareHelper.class));
        conventionAware.getConventionMapping().map("prop", TEST_CLOSURE);
    }

    @Test
    public void mixesInDynamicObjectAwareInterface() throws Exception {
        Class<? extends Bean> generatedClass = generator.generate(Bean.class);
        assertTrue(DynamicObjectAware.class.isAssignableFrom(generatedClass));
        Bean bean = JavaReflectionUtil.newInstance(generatedClass);
        DynamicObjectAware dynamicBean = (DynamicObjectAware) bean;

        dynamicBean.getAsDynamicObject().setProperty("prop", "value");
        assertThat(bean.getProp(), equalTo("value"));
        assertThat(bean.doStuff("some value"), equalTo("{some value}"));
    }

    @Test
    public void mixesInExtensionAwareInterface() throws Exception {
        Class<? extends Bean> generatedClass = generator.generate(Bean.class);
        assertTrue(ExtensionAware.class.isAssignableFrom(generatedClass));
        Bean bean = JavaReflectionUtil.newInstance(generatedClass);
        ExtensionAware dynamicBean = (ExtensionAware) bean;

        assertThat(dynamicBean.getExtensions(), notNullValue());
    }

    @Test
    public void mixesInGroovyObjectInterface() throws Exception {
        Class<? extends Bean> generatedClass = generator.generate(Bean.class);
        assertTrue(GroovyObject.class.isAssignableFrom(generatedClass));
        Bean bean = JavaReflectionUtil.newInstance(generatedClass);
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

        bean = JavaReflectionUtil.newInstance(generatedClass);
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
        assertThat(wildcard.getUpperBounds()[0], equalTo((Type) String.class));
        assertThat(wildcard.getLowerBounds().length, equalTo(0));

        // Callable<? super String>
        paramType = constructor.getGenericParameterTypes()[3];
        assertThat(paramType, instanceOf(ParameterizedType.class));
        parameterizedType = (ParameterizedType) paramType;
        assertThat(parameterizedType.getRawType(), equalTo((Type) Callable.class));
        assertThat(parameterizedType.getActualTypeArguments()[0], instanceOf(WildcardType.class));
        wildcard = (WildcardType) parameterizedType.getActualTypeArguments()[0];
        assertThat(wildcard.getUpperBounds().length, equalTo(1));
        assertThat(wildcard.getUpperBounds()[0], equalTo((Type) Object.class));
        assertThat(wildcard.getLowerBounds().length, equalTo(1));
        assertThat(wildcard.getLowerBounds()[0], equalTo((Type) String.class));

        // Callable<?>
        paramType = constructor.getGenericParameterTypes()[4];
        assertThat(paramType, instanceOf(ParameterizedType.class));
        parameterizedType = (ParameterizedType) paramType;
        assertThat(parameterizedType.getRawType(), equalTo((Type) Callable.class));
        assertThat(parameterizedType.getActualTypeArguments()[0], instanceOf(WildcardType.class));
        wildcard = (WildcardType) parameterizedType.getActualTypeArguments()[0];
        assertThat(wildcard.getUpperBounds().length, equalTo(1));
        assertThat(wildcard.getUpperBounds()[0], equalTo((Type) Object.class));
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

        assertThat(paramType, equalTo((Type) String[].class));
        assertThat(((Class<?>) paramType).getComponentType(), equalTo((Type) String.class));

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
        Bean bean = newInstance(BeanWithConstructor.class, "value");
        assertThat(bean.getClass(), sameInstance((Object) generator.generate(BeanWithConstructor.class)));
        assertThat(bean.getProp(), equalTo("value"));

        bean = newInstance(BeanWithConstructor.class);
        assertThat(bean.getProp(), equalTo("default value"));

        bean = newInstance(BeanWithConstructor.class, 127);
        assertThat(bean.getProp(), equalTo("127"));
    }

    @Test
    public void reportsConstructionFailure() {
        try {
            newInstance(UnconstructibleBean.class);
            fail();
        } catch (ObjectInstantiationException e) {
            assertThat(e.getCause(), sameInstance(UnconstructibleBean.failure));
        }

        try {
            newInstance(Bean.class, "arg1", 2);
            fail();
        } catch (ObjectInstantiationException e) {
            // expected
        }

        try {
            newInstance(AbstractBean.class);
            fail();
        } catch (GradleException e) {
            assertThat(e.getMessage(), equalTo("Cannot create a proxy class for abstract class 'AbstractBean'."));
        }

        try {
            newInstance(PrivateBean.class);
            fail();
        } catch (GradleException e) {
            assertThat(e.getMessage(), equalTo("Cannot create a proxy class for private class 'PrivateBean'."));
        }

        try {
            newInstance(FinalBean.class);
            fail();
        } catch (GradleException e) {
            assertThat(e.getMessage(), equalTo("Cannot create a proxy class for final class 'FinalBean'."));
        }
    }

    @Test
    public void appliesConventionMappingToEachProperty() throws Exception {
        Class<? extends Bean> generatedClass = generator.generate(Bean.class);
        assertTrue(IConventionAware.class.isAssignableFrom(generatedClass));
        Bean bean = JavaReflectionUtil.newInstance(generatedClass);
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
    public void appliesConventionMappingToPropertyWithMultipleSetters() throws Exception {
        BeanWithVariousGettersAndSetters bean = newInstance(BeanWithVariousGettersAndSetters.class);
        new DslObject(bean).getConventionMapping().map("overloaded", new Callable<String>() {
            public String call() {
                return "conventionValue";
            }
        });

        assertThat(bean.getOverloaded(), equalTo("conventionValue"));

        bean.setOverloaded("value");
        assertThat(bean.getOverloaded(), equalTo("chars = value"));

        bean = newInstance(BeanWithVariousGettersAndSetters.class);
        new DslObject(bean).getConventionMapping().map("overloaded", new Callable<String>() {
            public String call() {
                return "conventionValue";
            }
        });

        assertThat(bean.getOverloaded(), equalTo("conventionValue"));

        bean.setOverloaded(12);
        assertThat(bean.getOverloaded(), equalTo("number = 12"));

        bean = newInstance(BeanWithVariousGettersAndSetters.class);
        new DslObject(bean).getConventionMapping().map("overloaded", new Callable<String>() {
            public String call() {
                return "conventionValue";
            }
        });

        assertThat(bean.getOverloaded(), equalTo("conventionValue"));

        bean.setOverloaded(true);
        assertThat(bean.getOverloaded(), equalTo("object = true"));
    }

    @Test
    public void appliesConventionMappingToPropertyWithGetterCovariantType() throws Exception {
        CovariantPropertyTypes bean = newInstance(CovariantPropertyTypes.class);

        new DslObject(bean).getConventionMapping().map("value", new Callable<String>() {
            public String call() {
                return "conventionValue";
            }
        });

        assertThat(bean.getValue(), equalTo("conventionValue"));

        bean.setValue(12);
        assertThat(bean.getValue(), equalTo("12"));
    }

    @Test
    public void appliesConventionMappingToProtectedMethods() throws Exception {
        BeanWithNonPublicProperties bean = newInstance(BeanWithNonPublicProperties.class);

        assertThat(bean.getPackageProtected(), equalTo("package-protected"));
        assertThat(bean.getProtected(), equalTo("protected"));
        assertThat(bean.getPrivate(), equalTo("private"));

        IConventionAware conventionAware = (IConventionAware) bean;
        conventionAware.getConventionMapping().map("packageProtected", new Callable<String>() {
            public String call() {
                return "1";
            }
        });
        conventionAware.getConventionMapping().map("protected", new Callable<String>() {
            public String call() {
                return "2";
            }
        });

        assertThat(bean.getPackageProtected(), equalTo("1"));
        assertThat(bean.getProtected(), equalTo("2"));
    }

    @Test
    @Issue("GRADLE-2163")
    public void appliesConventionMappingToGroovyBoolean() throws Exception {
        BeanWithGroovyBoolean bean = JavaReflectionUtil.newInstance(generator.generate(BeanWithGroovyBoolean.class));

        assertTrue(bean instanceof IConventionAware);
        assertThat(bean.getSmallB(), equalTo(false));
        assertThat(bean.getBigB(), nullValue());
        assertThat(bean.getMixedB(), equalTo(false));

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

        conventionAware.getConventionMapping().map("mixedB", new Callable<Object>() {
            public Object call() throws Exception {
                return Boolean.TRUE;
            }
        });

        assertThat(bean.getMixedB(), equalTo(true));
        assertThat(bean.isMixedB(), equalTo(Boolean.TRUE));
    }

    @Test
    public void appliesConventionMappingToCollectionGetter() throws Exception {
        Class<? extends CollectionBean> generatedClass = generator.generate(CollectionBean.class);
        CollectionBean bean = JavaReflectionUtil.newInstance(generatedClass);
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
        BeanWithVariousPropertyTypes bean = JavaReflectionUtil.newInstance(generator.generate(BeanWithVariousPropertyTypes.class));

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
        ConventionAwareBean bean = JavaReflectionUtil.newInstance(generatedClass);
        assertSame(bean, bean.getConventionMapping());

        bean.setProp("value");
        assertEquals("[value]", bean.getProp());
    }

    @Test
    public void doesNotOverrideMethodsFromSuperclassesMarkedWithAnnotation() throws Exception {
        BeanSubClass bean = JavaReflectionUtil.newInstance(generator.generate(BeanSubClass.class));
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
        NoMappingBean bean = JavaReflectionUtil.newInstance(generator.generate(NoMappingBean.class));
        assertFalse(bean instanceof IConventionAware);
        assertNull(bean.getInterfaceProperty());

        // Check dynamic object behaviour still works
        assertTrue(bean instanceof DynamicObjectAware);
    }

    @Test
    public void doesNotOverrideMethodsFromDynamicObjectAwareInterface() throws Exception {
        DynamicObjectAwareBean bean = JavaReflectionUtil.newInstance(generator.generate(DynamicObjectAwareBean.class));
        assertThat(bean.getConvention(), sameInstance(bean.conv));
        assertThat(bean.getAsDynamicObject(), sameInstance(bean.conv.getExtensionsAsDynamicObject()));
    }

    @Test
    public void canAddDynamicPropertiesAndMethodsToJavaObject() throws Exception {
        Bean bean = JavaReflectionUtil.newInstance(generator.generate(Bean.class));
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
        TestDecoratedGroovyBean bean = JavaReflectionUtil.newInstance(generator.generate(TestDecoratedGroovyBean.class));
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
        Bean bean = JavaReflectionUtil.newInstance(generator.generate(Bean.class));

        call("{ it.metaClass.getConventionProperty = { -> 'value'} }", bean);
        assertThat(call("{ it.hasProperty('conventionProperty') }", bean), notNullValue());
        assertThat(call("{ it.getConventionProperty() }", bean), equalTo((Object) "value"));
        assertThat(call("{ it.conventionProperty }", bean), equalTo((Object) "value"));
    }

    @Test
    public void respectsPropertiesAddedToMetaClassOfGroovyObject() throws Exception {
        TestDecoratedGroovyBean bean = JavaReflectionUtil.newInstance(generator.generate(TestDecoratedGroovyBean.class));

        call("{ it.metaClass.getConventionProperty = { -> 'value'} }", bean);
        assertThat(call("{ it.hasProperty('conventionProperty') }", bean), notNullValue());
        assertThat(call("{ it.getConventionProperty() }", bean), equalTo((Object) "value"));
        assertThat(call("{ it.conventionProperty }", bean), equalTo((Object) "value"));
    }

    @Test
    public void usesExistingGetAsDynamicObjectMethod() throws Exception {
        DynamicObjectBean bean = JavaReflectionUtil.newInstance(generator.generate(DynamicObjectBean.class));

        call("{ it.prop = 'value' }", bean);
        assertThat(call("{ it.prop }", bean), equalTo((Object) "value"));

        bean.getAsDynamicObject().setProperty("prop", "value2");
        assertThat(call("{ it.prop }", bean), equalTo((Object) "value2"));

        call("{ it.ext.anotherProp = 12 }", bean);
        assertThat(bean.getAsDynamicObject().getProperty("anotherProp"), equalTo((Object) 12));
        assertThat(call("{ it.anotherProp }", bean), equalTo((Object) 12));
    }

    @Test
    public void constructorCanCallGetter() throws Exception {
        BeanUsesPropertiesInConstructor bean = newInstance(BeanUsesPropertiesInConstructor.class);

        assertThat(bean.name, equalTo("default-name"));
    }

    @Test
    public void mixesInSetValueMethodForSingleValuedProperty() throws Exception {
        BeanWithVariousGettersAndSetters bean = JavaReflectionUtil.newInstance(generator.generate(BeanWithVariousGettersAndSetters.class));

        call("{ it.prop 'value'}", bean);
        assertThat(bean.getProp(), equalTo("value"));

        call("{ it.finalGetter 'another'}", bean);
        assertThat(bean.getFinalGetter(), equalTo("another"));

        call("{ it.writeOnly 12}", bean);
        assertThat(bean.writeOnly, equalTo(12));

        call("{ it.primitive 12}", bean);
        assertThat(bean.getPrimitive(), equalTo(12));

        call("{ it.bool true}", bean);
        assertThat(bean.isBool(), equalTo(true));

        call("{ it.overloaded 'value'}", bean);
        assertThat(bean.getOverloaded(), equalTo("chars = value"));

        call("{ it.overloaded 12}", bean);
        assertThat(bean.getOverloaded(), equalTo("number = 12"));

        call("{ it.overloaded true}", bean);
        assertThat(bean.getOverloaded(), equalTo("object = true"));
    }

    @Test
    public void doesNotUseConventionValueOnceSetValueMethodHasBeenCalled() throws Exception {
        Bean bean = JavaReflectionUtil.newInstance(generator.generate(Bean.class));
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
        BeanWithReadOnlyProperties bean = JavaReflectionUtil.newInstance(generator.generate(BeanWithReadOnlyProperties.class));

        try {
            call("{ it.prop 'value'}", bean);
            fail();
        } catch (MissingMethodException e) {
            assertThat(e.getMethod(), equalTo("prop"));
        }
    }

    @Test
    public void doesNotMixInSetValueMethodForMultiValueProperty() throws Exception {
        CollectionBean bean = JavaReflectionUtil.newInstance(generator.generate(CollectionBean.class));

        try {
            call("{ def val = ['value']; it.prop val}", bean);
            fail();
        } catch (MissingMethodException e) {
            assertThat(e.getMethod(), equalTo("prop"));
        }
    }

    @Test
    public void overridesExistingSetValueMethod() throws Exception {
        BeanWithDslMethods bean = JavaReflectionUtil.newInstance(generator.generate(BeanWithDslMethods.class));
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
    public void addsInsteadOfOverridesSetValueMethodIfOnlyMultiArgMethods() throws Exception {
        BeanWithMultiArgDslMethods bean = JavaReflectionUtil.newInstance(generator.generate(BeanWithMultiArgDslMethods.class));
        // this method should have been added to the class
        call("{ it.prop 'value'}", bean);
        assertThat(bean.getProp(), equalTo("value"));
    }

    @Test
    public void doesNotOverrideSetValueMethodForPropertyThatIsNotConventionMappingAware() throws Exception {
        BeanWithMultiArgDslMethodsAndNoConventionMapping bean = JavaReflectionUtil.newInstance(generator.generate(BeanWithMultiArgDslMethodsAndNoConventionMapping.class));
        call("{ it.prop 'value'}", bean);
        assertThat(bean.getProp(), equalTo("(value)"));
    }

    @Test
    public void mixesInClosureOverloadForActionMethod() throws Exception {
        Bean bean = JavaReflectionUtil.newInstance(generator.generate(Bean.class));
        bean.prop = "value";

        call("{def value; it.doStuff { value = it }; assert value == \'value\' }", bean);

        BeanWithOverriddenMethods subBean = JavaReflectionUtil.newInstance(generator.generate(BeanWithOverriddenMethods.class));

        call("{def value; it.doStuff { value = it }; assert value == \'overloaded\' }", subBean);
    }

    @Test
    public void doesNotOverrideExistingClosureOverload() throws Exception {
        BeanWithDslMethods bean = JavaReflectionUtil.newInstance(generator.generate(BeanWithDslMethods.class));
        bean.prop = "value";

        assertThat(call("{def value; it.doStuff { value = it }; return value }", bean), equalTo((Object) "[value]"));
    }

    @Test
    public void generatesDslObjectCompatibleObject() throws Exception {
        DslObject dslObject = new DslObject(JavaReflectionUtil.newInstance(generator.generate(Bean.class)));
        assertEquals(Bean.class, dslObject.getDeclaredType());
        assertEquals(typeOf(Bean.class), dslObject.getPublicType());
        assertNotNull(dslObject.getConventionMapping());
        assertNotNull(dslObject.getConvention());
        assertNotNull(dslObject.getExtensions());
        assertNotNull(dslObject.getAsDynamicObject());
    }

    @Test
    public void honorsPublicType() throws Exception {
        DslObject dslObject = new DslObject(JavaReflectionUtil.newInstance(generator.generate(BeanWithPublicType.class)));
        assertEquals(typeOf(Bean.class), dslObject.getPublicType());
    }

    @Test
    public void includesNotInheritedTypeAnnotations() throws IllegalAccessException, InstantiationException {
        Class<? extends AnnotatedBean> generatedClass = generator.generate(AnnotatedBean.class);

        BeanAnnotation annotation = generatedClass.getAnnotation(BeanAnnotation.class);
        assertThat(annotation, notNullValue());
        assertThat(annotation.value(), equalTo("test"));
        assertThat(annotation.values(), equalTo(new String[]{"1", "2"}));
        assertThat(annotation.enumValue(), equalTo(AnnotationEnum.A));
        assertThat(annotation.enumValues(), equalTo(new AnnotationEnum[]{AnnotationEnum.A, AnnotationEnum.B}));
        assertThat(annotation.number(), equalTo(1));
        assertThat(annotation.numbers(), equalTo(new int[]{1, 2}));
        assertThat(annotation.clazz().equals(Integer.class), equalTo(true));
        assertThat(annotation.classes(), equalTo(new Class<?>[]{Integer.class}));
        assertThat(annotation.annotation().value(), equalTo("nested"));
        assertThat(annotation.annotations()[0].value(), equalTo("nested array"));
    }

    @Test
    public void generatedTypeIsMarkedSynthetic() {
        assertTrue(generator.generate(Bean.class).isSynthetic());
    }

    @Test
    public void addsSetterMethodsForPropertyWhoseTypeIsProperty() throws Exception {
        DefaultProviderFactory providerFactory = new DefaultProviderFactory();
        BeanWithProperty bean = newInstance(BeanWithProperty.class, TestUtil.objectFactory());

        DynamicObject dynamicObject = ((DynamicObjectAware) bean).getAsDynamicObject();

        dynamicObject.setProperty("prop", "value");
        assertEquals("value", bean.getProp().get());

        dynamicObject.setProperty("prop", providerFactory.provider(new Callable<String>() {
            int count;
            @Override
            public String call() throws Exception {
                return "[" + String.valueOf(++count) + "]";
            }
        }));
        assertEquals("[1]", bean.getProp().get());
        assertEquals("[2]", bean.getProp().get());
    }

    @Test
    public void addsSetterMethodsForPropertyWhoseTypeIsPropertyAndCapitalizedProperly() throws Exception {
        DefaultProviderFactory providerFactory = new DefaultProviderFactory();
        BeanWithProperty bean = newInstance(BeanWithProperty.class, TestUtil.objectFactory());

        DynamicObject dynamicObject = ((DynamicObjectAware) bean).getAsDynamicObject();

        dynamicObject.setProperty("aProp", "value");
        assertEquals("value", bean.getaProp().get());

        dynamicObject.setProperty("aProp", providerFactory.provider(new Callable<String>() {
            int count;
            @Override
            public String call() throws Exception {
                return "[" + String.valueOf(++count) + "]";
            }
        }));
        assertEquals("[1]", bean.getaProp().get());
        assertEquals("[2]", bean.getaProp().get());
    }

    @Test
    public void doesNotAddSetterMethodsForPropertyWhoseTypeIsPropertyWhenTheSetterMethodsAlreadyExist() throws Exception {
        DefaultProviderFactory providerFactory = new DefaultProviderFactory();
        BeanWithProperty bean = newInstance(BeanWithProperty.class, TestUtil.objectFactory());

        DynamicObject dynamicObject = ((DynamicObjectAware) bean).getAsDynamicObject();

        dynamicObject.setProperty("prop2", "value");
        assertEquals("[value]", bean.getProp2().get());

        dynamicObject.setProperty("prop2", 12);
        assertEquals("[12]", bean.getProp2().get());

        dynamicObject.setProperty("prop2", providerFactory.provider(new Callable<String>() {
            int count;
            @Override
            public String call() throws Exception {
                return "[" + String.valueOf(++count) + "]";
            }
        }));
        assertEquals("[1]", bean.getProp2().get());
        assertEquals("[2]", bean.getProp2().get());
    }

    private <T> T newInstance(Class<T> type, Object... parameters) {
        return DirectInstantiator.instantiate(generator.generate(type), parameters);
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

    public static class BeanWithPublicType extends Bean implements HasPublicType {
        @Override
        public TypeOf<?> getPublicType() {
            return typeOf(Bean.class);
        }
    }

    public static class BeanWithOverriddenMethods extends Bean {
        @Override
        public String getProp() {
            return super.getProp();
        }

        @Override
        public void setProp(String prop) {
            super.setProp(prop);
        }

        @Override
        public String doStuff(String value) {
            return super.doStuff(value);
        }

        @Override
        public void doStuff(Action<String> action) {
            action.execute("overloaded");
        }
    }

    public static class ParentBean {
        Object value;

        public Object getValue() {
            return value;
        }

        public void setValue(Object value) {
            this.value = value;
        }
    }

    public static class CovariantPropertyTypes extends ParentBean {
        @Override
        public String getValue() {
            return String.valueOf(super.getValue());
        }
    }

    public static class BeanWithReadOnlyProperties {
        public String getProp() {
            return "value";
        }
    }

    public static class BeanWithNonPublicProperties {
        String getPackageProtected() {
            return "package-protected";
        }

        protected String getProtected() {
            return "protected";
        }

        private String getPrivate() {
            return "private";
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

    public static class BeanWithMultiArgDslMethods extends Bean {
        private String prop;

        public String getProp() {
            return prop;
        }

        public void setProp(String prop) {
            this.prop = prop;
        }

        public BeanWithMultiArgDslMethods prop(String part1, String part2) {
            this.prop = String.format("<%s%s>", part1, part2);
            return this;
        }

        public BeanWithMultiArgDslMethods prop(String part1, String part2, String part3) {
            this.prop = String.format("[%s%s%s]", part1, part2, part3);
            return this;
        }
    }

    @NoConventionMapping
    public static class BeanWithMultiArgDslMethodsAndNoConventionMapping extends Bean {
        private String prop;

        public String getProp() {
            return prop;
        }

        public void setProp(String prop) {
            this.prop = prop;
        }

        public void prop(String value) {
            this.prop = String.format("(%s)", value);
        }

        public void prop(String part1, String part2) {
            this.prop = String.format("<%s%s>", part1, part2);
        }

        public void prop(String part1, String part2, String part3) {
            this.prop = String.format("[%s%s%s]", part1, part2, part3);
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
        Convention conv = new ExtensibleDynamicObject(this, DynamicObjectAwareBean.class, ThreadGlobalInstantiator.getOrCreate()).getConvention();

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
        private String overloaded;

        public int getPrimitive() {
            return primitive;
        }

        public void setPrimitive(int primitive) {
            this.primitive = primitive;
        }

        public boolean isBool() {
            return bool;
        }

        public void setBool(boolean bool) {
            this.bool = bool;
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

        public String getOverloaded() {
            return overloaded;
        }

        public void setOverloaded(Number overloaded) {
            this.overloaded = String.format("number = %s", overloaded);
        }

        public void setOverloaded(CharSequence overloaded) {
            this.overloaded = String.format("chars = %s", overloaded);
        }

        public void setOverloaded(Object overloaded) {
            this.overloaded = String.format("object = %s", overloaded);
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

    public static class UnconstructibleBean {
        static Throwable failure = new UnsupportedOperationException();

        public UnconstructibleBean() throws Throwable {
            throw failure;
        }
    }

    public static abstract class AbstractBean {
        abstract void implementMe();
    }

    public static final class FinalBean {
    }


    private static class PrivateBean {
    }

    public enum AnnotationEnum {
        A, B
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE)
    public static @interface NestedBeanAnnotation {
        String value();
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE)
    public static @interface BeanAnnotation {
        String value();

        String[] values();

        AnnotationEnum enumValue();

        AnnotationEnum[] enumValues();

        int number();

        int[] numbers();

        Class<?> clazz();

        Class<?>[] classes();

        NestedBeanAnnotation annotation();

        NestedBeanAnnotation[] annotations();
    }

    @BeanAnnotation(
        value = "test",
        values = {"1", "2"},
        enumValue = AnnotationEnum.A,
        enumValues = {AnnotationEnum.A, AnnotationEnum.B},
        number = 1,
        numbers = {1, 2},
        clazz = Integer.class,
        classes = {Integer.class},
        annotation = @NestedBeanAnnotation("nested"),
        annotations = {@NestedBeanAnnotation("nested array")}
    )
    public static class AnnotatedBean {
    }

    public static class BeanWithProperty {
        private final Property<String> prop;
        private final Property<String> prop2;
        private final Property<String> aProp;

        public BeanWithProperty(ObjectFactory factory) {
            this.prop = factory.property(String.class);
            this.prop2 = factory.property(String.class);
            this.aProp = factory.property(String.class);
        }

        public Property<String> getProp() {
            return prop;
        }

        public Property<String> getProp2() {
            return prop2;
        }

        public void setProp2(String value) {
            prop2.set("[" + value + "]");
        }

        public void setProp2(int value) {
            prop2.set("[" + value + "]");
        }

        public Property<String> getaProp() {
            return aProp;
        }
    }

}
