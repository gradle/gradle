/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.internal.reflect;

import org.gradle.api.InvalidUserCodeException;
import org.gradle.api.reflect.PublicType;
import org.gradle.api.reflect.PublicTypeSupplier;
import org.gradle.api.reflect.TypeOf;
import org.junit.Test;

import static java.util.Collections.singletonList;
import static org.gradle.api.reflect.TypeOf.typeOf;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class PublicTypeAnnotationDetectorTest {

    @Test
    public void returnsNullWhenNoPublicTypeAnnotationIsFound() {
        assertThat(PublicTypeAnnotationDetector.detect(Object.class), nullValue());
    }

    @Test
    public void throwsWhenPublicTypeAnnotationHasBothTypeAndSupplierParameters() {
        try {
            PublicTypeAnnotationDetector.detect(AnnotatedWithBothParameters.class);
            fail("Should have failed");
        } catch (InvalidUserCodeException ex) {
            assertThat(
                ex.getMessage(),
                equalTo(
                    "Public type detection for " + AnnotatedWithBothParameters.class + " failed: " +
                        "@PublicType annotation on " + AnnotatedWithBothParameters.class +
                        " must not provide both `type` and `supplier` parameters."
                )
            );
        }
        try {
            PublicTypeAnnotationDetector.detect(ConcreteImplementationAnnotatedWithBothParameters.class);
            fail("Should have failed");
        } catch (InvalidUserCodeException ex) {
            assertThat(
                ex.getMessage(),
                equalTo(
                    "Public type detection for " + ConcreteImplementationAnnotatedWithBothParameters.class + " failed: " +
                        "@PublicType annotation on " + AnnotatedWithBothParameters.class +
                        " must not provide both `type` and `supplier` parameters."
                )
            );
        }
    }

    @Test
    public void reportsAnnotatedInterfaceAsAnError() {
        try {
            PublicTypeAnnotationDetector.detect(AnnotatedInterface.class);
            fail("Should have failed");
        } catch (InvalidUserCodeException ex) {
            assertThat(
                ex.getMessage(),
                equalTo(
                    "Public type detection for " + AnnotatedInterface.class + " failed: " +
                        "@PublicType annotation should only be used on classes, not on " + AnnotatedInterface.class + "."
                )
            );
        }
        try {
            PublicTypeAnnotationDetector.detect(ClassWithAnnotatedInterface.class);
            fail("Should have failed");
        } catch (InvalidUserCodeException ex) {
            assertThat(
                ex.getMessage(),
                equalTo(
                    "Public type detection for " + ClassWithAnnotatedInterface.class + " failed: " +
                        "@PublicType annotation should only be used on classes, not on " + AnnotatedInterface.class + "."
                )
            );
        }
    }

    @Test
    public void findsFirstPublicTypeAnnotationInSuperClasses() {
        assertThat(
            PublicTypeAnnotationDetector.detect(ConcreteImplementationA.class),
            equalTo(typeOf(AbstractImplementationA.class))
        );
    }

    @Test
    public void detectsGivenPublicType() {
        assertThat(
            PublicTypeAnnotationDetector.detect(ConcreteImplementationB.class),
            equalTo(typeOf(InterfaceTypeB.class))
        );
    }

    @Test
    public void detectsGivenPublicTypeSupplier() {
        assertThat(
            PublicTypeAnnotationDetector.detect(ConcreteImplementationC.class),
            equalTo(typeOf(InterfaceTypeC.class))
        );
    }

    @Test
    public void detectsGivenPublicTypeSupplierWithGenerics() {
        TypeOf<?> type = PublicTypeAnnotationDetector.detect(ConcreteImplementationD.class);
        assertThat(type.getConcreteClass(), equalTo(InterfaceTypeD.class));
        assertTrue(type.isParameterized());
        assertThat(type.getActualTypeArguments(), equalTo(singletonList(typeOf(String.class))));
    }

    @Test
    public void failsWhenPublicTypeSupplierHasNoPublicNoArgConstructor() {
        try {
            PublicTypeAnnotationDetector.detect(ClassWithPrivateConstructorPublicTypeSupplier.class);
            fail("Should have failed");
        } catch (InvalidUserCodeException ex) {
            assertThat(
                ex.getMessage(),
                equalTo(
                    "Public type detection for " + ClassWithPrivateConstructorPublicTypeSupplier.class + " failed: " +
                        "@PublicType annotation on " + ClassWithPrivateConstructorPublicTypeSupplier.class +
                        " has supplier " + PrivateConstructorPublicTypeSupplier.class +
                        " which could not be instantiated, a public default constructor is required."
                )
            );
        }
    }

    @PublicType
    public interface AnnotatedInterface {}

    public static class ClassWithAnnotatedInterface implements AnnotatedInterface {}

    @PublicType(type = Object.class, supplier = SomePublicTypeSupplier.class)
    public static class AnnotatedWithBothParameters {}

    public static class SomePublicTypeSupplier implements PublicTypeSupplier {

        @Override
        public TypeOf<?> getPublicType() {
            throw new UnsupportedOperationException("Not used in test");
        }
    }

    public static class ConcreteImplementationAnnotatedWithBothParameters extends AnnotatedWithBothParameters {}

    public interface InterfaceTypeA {}

    @PublicType
    public static class AbstractImplementationA implements InterfaceTypeA {}

    public static class ConcreteImplementationA extends AbstractImplementationA {}

    public interface InterfaceTypeB {}

    @PublicType(type = InterfaceTypeB.class)
    public static class AbstractImplementationB implements InterfaceTypeB {}

    public static class ConcreteImplementationB extends AbstractImplementationB {}

    public interface InterfaceTypeC {}

    @PublicType(supplier = PublicTypeSupplierC.class)
    public static class AbstractImplementationC implements InterfaceTypeC {}

    public static class ConcreteImplementationC extends AbstractImplementationC {}

    public static class PublicTypeSupplierC implements PublicTypeSupplier {

        @Override
        public TypeOf<?> getPublicType() {
            return typeOf(InterfaceTypeC.class);
        }
    }

    public interface InterfaceTypeD<T> {}

    @PublicType(supplier = PublicTypeSupplierD.class)
    public static class ConcreteImplementationD implements InterfaceTypeD<String> {}

    public static class PublicTypeSupplierD implements PublicTypeSupplier {

        @Override
        public TypeOf<?> getPublicType() {
            return new TypeOf<InterfaceTypeD<String>>() {};
        }
    }

    @PublicType(supplier = PrivateConstructorPublicTypeSupplier.class)
    public static class ClassWithPrivateConstructorPublicTypeSupplier {}

    public static class PrivateConstructorPublicTypeSupplier implements PublicTypeSupplier {

        private PrivateConstructorPublicTypeSupplier() {}

        @Override
        public TypeOf<?> getPublicType() {
            throw new UnsupportedOperationException("Not used in test");
        }
    }
}
