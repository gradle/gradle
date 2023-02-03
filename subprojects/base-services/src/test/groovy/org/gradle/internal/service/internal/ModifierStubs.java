/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.internal.service.internal;

import javax.inject.Inject;

/**
 * Intentionally exists in a different package than {@link org.gradle.internal.service.DefaultServiceRegistry}
 * to verify reflection is able to instantiate classes in a different package.
 */
@SuppressWarnings("unused")
public class ModifierStubs {

    public static class DefaultConstructor {
        // Default constructor
    }

    public static class PackagePrivateConstructor {
        final String testData;

        // This will be injected
        PackagePrivateConstructor(String testData) {
            this.testData = testData;
        }
    }

    public static class PackagePrivateConstructorWithPrivateConstructor {
        final String testData;

        // This is still ambiguous and will require the @Inject annotation to be fixed
        PackagePrivateConstructorWithPrivateConstructor() {
            this("Potato");
        }

        private PackagePrivateConstructorWithPrivateConstructor(String testData) {
            this.testData = testData;
        }
    }

    public static class AnnotatedPackagePrivateConstructorWithPrivateConstructor {
        final String testData;

        // This will be injected
        @Inject
        AnnotatedPackagePrivateConstructorWithPrivateConstructor() {
            this("Potato");
        }

        // This will not get injected
        private AnnotatedPackagePrivateConstructorWithPrivateConstructor(String testData) {
            this.testData = testData;
        }
    }

    public static class PublicConstructorWithPrivateConstructor {
        final String testData;

        // This is still ambiguous and will require the @Inject annotation to be fixed
        public PublicConstructorWithPrivateConstructor() {
            this("Potato");
        }

        private PublicConstructorWithPrivateConstructor(String testData) {
            this.testData = testData;
        }
    }

    public static class AnnotatedPublicConstructorWithPrivateConstructor {
        final String testData;

        // This will be injected
        @Inject
        public AnnotatedPublicConstructorWithPrivateConstructor() {
            this("Potato");
        }

        private AnnotatedPublicConstructorWithPrivateConstructor(String testData) {
            this.testData = testData;
        }
    }

    @SuppressWarnings("InnerClassMayBeStatic")
    public class NonStaticInnerClass {
        // This will fail to be injected because this is not a static inner class
        NonStaticInnerClass() {
            /* no-op */
        }
    }
}
