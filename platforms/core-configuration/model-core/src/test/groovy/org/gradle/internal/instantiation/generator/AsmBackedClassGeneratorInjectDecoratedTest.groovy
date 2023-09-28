/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.internal.instantiation.generator

import org.gradle.api.plugins.ExtensionAware
import org.gradle.api.plugins.ExtensionContainer
import org.gradle.cache.internal.TestCrossBuildInMemoryCacheFactory
import org.gradle.internal.instantiation.ClassGenerationException
import org.gradle.internal.instantiation.InjectAnnotationHandler
import org.gradle.internal.instantiation.InstantiatorFactory
import org.gradle.internal.instantiation.PropertyRoleAnnotationHandler
import org.gradle.internal.service.DefaultServiceRegistry
import org.gradle.internal.service.ServiceRegistry
import org.gradle.util.TestUtil

import javax.inject.Inject
import java.lang.annotation.Annotation
import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type

import static AsmBackedClassGeneratorTest.AbstractClassRealizingTwoTypeParameters
import static AsmBackedClassGeneratorTest.AbstractClassWithConcreteTypeParameter
import static AsmBackedClassGeneratorTest.AbstractClassWithParameterizedTypeParameter
import static AsmBackedClassGeneratorTest.FinalInjectBean
import static AsmBackedClassGeneratorTest.NonGetterInjectBean
import static AsmBackedClassGeneratorTest.PrivateInjectBean

class AsmBackedClassGeneratorInjectDecoratedTest extends AbstractClassGeneratorSpec {
    ClassGenerator generator = AsmBackedClassGenerator.decorateAndInject([], Stub(PropertyRoleAnnotationHandler), [], new TestCrossBuildInMemoryCacheFactory(), 0)

    def "can inject service using @Inject on a getter method with dummy method body"() {
        given:
        def services = defaultServices()
        _ * services.get(Number) >> 12

        when:
        def obj = create(BeanWithServices, services)

        then:
        obj.thing == 12
        obj.getThing() == 12
        obj.getProperty("thing") == 12
    }

    def "can inject service using @Inject on an abstract service getter method"() {
        given:
        def services = defaultServices()
        _ * services.get(Number) >> 12

        when:
        def obj = create(AbstractBeanWithServices, services)

        then:
        obj.thing == 12
        obj.getThing() == 12
        obj.getProperty("thing") == 12
    }

    def "can inject service using @Inject on a super interface with class type parameter"() {
        given:
        def services = defaultServices()
        _ * services.get(Number) >> 12

        when:
        def obj = create(AbstractClassWithConcreteTypeParameter, services)

        then:
        obj.thing == 12
        obj.getThing() == 12
        obj.getProperty("thing") == 12
        obj.doSomething()

        def returnType = obj.getClass().getDeclaredMethod("getThing").genericReturnType
        returnType == Number
    }

    def "can inject services using @Inject on a super interface with type parameter remapping"() {
        given:
        def services = defaultServices()
        _ * services.get(_) >> { Type type ->
            if (type instanceof ParameterizedType) {
                assert type.rawType == List.class
                assert type.actualTypeArguments.length == 1
                assert type.actualTypeArguments[0] == String
                return ["Hello", "Number"]
            }
            assert type == Number
            return 12
        }

        when:
        def obj = create(AbstractClassRealizingTwoTypeParameters, services)

        then:
        obj.thing == 12
        obj.getThing() == 12
        obj.getProperty("thing") == 12
        obj.getOtherThing() == ["Hello", "Number"]
        obj.doSomething() == "Hello Number 12"

        def returnType = obj.getClass().getDeclaredMethod("getThing").genericReturnType
        returnType == Number.class
        def otherReturnType = obj.getClass().getDeclaredMethod("getOtherThing").genericReturnType
        otherReturnType instanceof ParameterizedType
        otherReturnType.rawType == List
        otherReturnType.actualTypeArguments.length == 1
        otherReturnType.actualTypeArguments[0] == String
    }

    def "can inject service using @Inject on a super interface with parameterized type parameters"() {
        given:
        def services = defaultServices()
        _ * services.get(_) >> { Type type ->
            assert type instanceof ParameterizedType
            assert type.rawType == List.class
            assert type.actualTypeArguments.length == 1
            assert type.actualTypeArguments[0] == Number
            return [12]
        }

        when:
        def obj = create(AbstractClassWithParameterizedTypeParameter, services)

        then:
        obj.thing == [12]
        obj.getThing() == [12]
        obj.getProperty("thing") == [12]
        obj.doSomething() == "[12]"

        def returnType = obj.getClass().getDeclaredMethod("getThing").genericReturnType
        returnType instanceof ParameterizedType
        returnType.rawType == List
        returnType.actualTypeArguments.length == 1
        returnType.actualTypeArguments[0] == Number
    }

    def "can inject service using @Inject on an interface getter method"() {
        given:
        def services = defaultServices()
        _ * services.get(Number) >> 12

        when:
        def obj = create(InterfaceWithServices, services)

        then:
        obj.thing == 12
        obj.getThing() == 12
        obj.getProperty("thing") == 12
    }

    def "can optionally set injected service using a service setter method"() {
        given:
        def services = defaultServices()

        when:
        def obj = create(BeanWithMutableServices, services)
        obj.thing = 12

        then:
        obj.thing == 12
        obj.getThing() == 12
        obj.getProperty("thing") == 12
    }

    def "retains declared generic type of service getter"() {
        given:
        def services = defaultServices()
        _ * services.get(_) >> { Type type ->
            assert type instanceof ParameterizedType
            assert type.rawType == List.class
            assert type.actualTypeArguments.length == 1
            assert type.actualTypeArguments[0] == Number
            return [12]
        }

        when:
        def obj = create(BeanWithParameterizedTypeService, services)

        then:
        obj.things == [12]
        obj.getThings() == [12]
        obj.getProperty("things") == [12]

        def returnType = obj.getClass().getDeclaredMethod("getThings").genericReturnType
        assert returnType instanceof ParameterizedType
        assert returnType.rawType == List.class
        assert returnType.actualTypeArguments.length == 1
        assert returnType.actualTypeArguments[0] == Number
    }

    def "service lookup is lazy and the result is cached"() {
        given:
        def services = defaultServices()

        when:
        def obj = create(BeanWithServices, services)

        then:
        0 * services._

        when:
        obj.thing

        then:
        1 * services.get(Number) >> 12
        0 * services.get(Number)

        when:
        obj.thing

        then:
        0 * services._
    }

    def "can inject service using a custom annotation on getter method with dummy method body"() {
        given:
        def services = defaultServices()
        _ * services.get(Number, CustomInject) >> 12

        def generator = AsmBackedClassGenerator.decorateAndInject([new CustomAnnotationHandler()], Stub(PropertyRoleAnnotationHandler), [CustomInject], new TestCrossBuildInMemoryCacheFactory(), 0)

        when:
        def obj = create(generator, BeanWithCustomServices, services)

        then:
        obj.thing == 12
        obj.getThing() == 12
        obj.getProperty("thing") == 12
    }

    def "can inject service using a custom annotation on abstract getter method"() {
        given:
        def services = defaultServices()
        _ * services.get(Number, CustomInject) >> 12

        def generator = AsmBackedClassGenerator.decorateAndInject([new CustomAnnotationHandler()], Stub(PropertyRoleAnnotationHandler), [CustomInject], new TestCrossBuildInMemoryCacheFactory(), 0)

        when:
        def obj = create(generator, AbstractBeanWithCustomServices, services)

        then:
        obj.thing == 12
        obj.getThing() == 12
        obj.getProperty("thing") == 12
    }

    def "cannot use multiple inject annotations on getter"() {
        def generator = AsmBackedClassGenerator.decorateAndInject([new CustomAnnotationHandler()], Stub(PropertyRoleAnnotationHandler), [CustomInject], new TestCrossBuildInMemoryCacheFactory(), 0)

        when:
        create(generator, MultipleInjectAnnotations)

        then:
        def e = thrown(ClassGenerationException)
        e.cause.message == "Cannot use @Inject and @CustomInject annotations together on method MultipleInjectAnnotations.getBoth()."
    }

    def "object can provide its own service registry to provide services for injection"() {
        given:
        def services = defaultServices()

        when:
        def obj = create(BeanWithServicesAndServiceRegistry, services)

        then:
        obj.thing == 12
        obj.getThing() == 12
        obj.getProperty("thing") == 12
    }

    def "cannot attach @Inject annotation to methods of ExtensionAware"() {
        when:
        generator.generate(ExtensibleBeanWithInject)

        then:
        def e = thrown(ClassGenerationException)
        e.cause.message == "Cannot use @Inject annotation on method ExtensibleBeanWithInject.getExtensions()."
    }

    def "cannot attach @Inject annotation to final method"() {
        when:
        generator.generate(FinalInjectBean)

        then:
        def e = thrown(ClassGenerationException)
        e.cause.message == "Cannot use @Inject annotation on method FinalInjectBean.getThing() as it is final."
    }

    def "cannot attach @Inject annotation to static method"() {
        when:
        generator.generate(StaticInjectBean)

        then:
        def e = thrown(ClassGenerationException)
        e.cause.message == "Cannot use @Inject annotation on method StaticInjectBean.getThing() as it is static."
    }

    def "cannot attach @Inject annotation to private method"() {
        when:
        generator.generate(PrivateInjectBean)

        then:
        def e = thrown(ClassGenerationException)
        e.cause.message == "Cannot use @Inject annotation on method PrivateInjectBean.getThing() as it is not public or protected."
    }

    def "cannot attach @Inject annotation to non getter method"() {
        when:
        generator.generate(NonGetterInjectBean)

        then:
        def e = thrown(ClassGenerationException)
        e.cause.message == "Cannot use @Inject annotation on method NonGetterInjectBean.thing() as it is not a property getter."
    }

    def "cannot attach custom annotation that is known but not enabled to getter method"() {
        def generator = AsmBackedClassGenerator.decorateAndInject([new CustomAnnotationHandler()], Stub(PropertyRoleAnnotationHandler), [], new TestCrossBuildInMemoryCacheFactory(), 0)

        when:
        create(generator, BeanWithCustomServices)

        then:
        def e = thrown(ClassGenerationException)
        e.cause.message == "Cannot use @CustomInject annotation on method BeanWithCustomServices.getThing()."
    }

    def "cannot attach custom annotation that is known but not enabled to static method"() {
        def generator = AsmBackedClassGenerator.decorateAndInject([new CustomAnnotationHandler()],Stub(PropertyRoleAnnotationHandler), [], new TestCrossBuildInMemoryCacheFactory(), 0)

        when:
        create(generator, StaticCustomInjectBean)

        then:
        def e = thrown(ClassGenerationException)
        e.cause.message == "Cannot use @CustomInject annotation on method StaticCustomInjectBean.getThing()."
    }

    def "cannot attach custom inject annotation to methods of ExtensionAware"() {
        def generator = AsmBackedClassGenerator.decorateAndInject([new CustomAnnotationHandler()], Stub(PropertyRoleAnnotationHandler), [CustomInject], new TestCrossBuildInMemoryCacheFactory(), 0)

        when:
        generator.generate(ExtensibleBeanWithCustomInject)

        then:
        def e = thrown(ClassGenerationException)
        e.cause.message == "Cannot use @CustomInject annotation on method ExtensibleBeanWithCustomInject.getExtensions()."
    }

    def "cannot attach custom inject annotation to static method"() {
        def generator = AsmBackedClassGenerator.decorateAndInject([new CustomAnnotationHandler()], Stub(PropertyRoleAnnotationHandler), [CustomInject], new TestCrossBuildInMemoryCacheFactory(), 0)

        when:
        generator.generate(StaticCustomInjectBean)
        fail()

        then:
        def e = thrown(ClassGenerationException)
        e.cause.message == "Cannot use @CustomInject annotation on method StaticCustomInjectBean.getThing() as it is static."
    }
}

class CustomAnnotationHandler implements InjectAnnotationHandler {
    @Override
    Class<? extends Annotation> getAnnotationType() {
        return CustomInject
    }
}

abstract class AbstractBeanWithServices {
    @Inject
    abstract Number getThing()
}

interface InterfaceWithServices {
    @Inject
    Number getThing()
}

class BeanWithServices {
    @Inject
    Number getThing() {
        throw new UnsupportedOperationException()
    }
}

class BeanWithMutableServices extends BeanWithServices {
    void setThing(Number number) {
        throw new UnsupportedOperationException()
    }
}

class BeanWithServicesAndServiceRegistry extends BeanWithServices {
    ServiceRegistry getServices() {
        def registry = new DefaultServiceRegistry()
        registry.add(InstantiatorFactory.class, TestUtil.instantiatorFactory())
        registry.add(Number, 12)
        return registry
    }
}

class BeanWithParameterizedTypeService {
    @Inject
    List<Number> getThings() {
        throw new UnsupportedOperationException()
    }
}

@Retention(RetentionPolicy.RUNTIME)
@interface CustomInject {
}

class BeanWithCustomServices {
    @CustomInject
    Number getThing() { throw new UnsupportedOperationException() }
}

abstract class AbstractBeanWithCustomServices {
    @CustomInject
    abstract Number getThing()
}

class MultipleInjectAnnotations {
    @Inject
    @CustomInject
    Number getBoth() {
        throw new UnsupportedOperationException()
    }
}

class ExtensibleBeanWithInject implements ExtensionAware {
    @Override
    @Inject
    ExtensionContainer getExtensions() {
        throw new UnsupportedOperationException()
    }
}

class ExtensibleBeanWithCustomInject implements ExtensionAware {
    @Override
    @CustomInject
    ExtensionContainer getExtensions() {
        throw new UnsupportedOperationException()
    }
}

class StaticInjectBean {
    @Inject
    static Number getThing() {
        throw new UnsupportedOperationException()
    }
}

class StaticCustomInjectBean {
    @CustomInject
    static Number getThing() {
        throw new UnsupportedOperationException()
    }
}
