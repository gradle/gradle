/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.model.internal.inspect;

@SuppressWarnings({"UnusedDeclaration", "deprecation"})
public class OuterClass {
    public static interface AnInterface {
    }

    public class InnerInstanceClass extends org.gradle.model.RuleSource {
    }

    private class PrivateInnerStaticClass {
    }

    public static class HasSuperclass extends InnerPublicStaticClass {
    }

    public static class DoesNotExtendRuleSource {
    }

    public static class HasTwoConstructors extends org.gradle.model.RuleSource {
        public HasTwoConstructors() {
        }

        public HasTwoConstructors(String arg) {
        }
    }

    public static class HasInstanceVar extends org.gradle.model.RuleSource {
        private String foo;
    }

    public static class HasFinalInstanceVar extends org.gradle.model.RuleSource {
        private final String foo = null;
    }

    public static class HasNonFinalStaticVar extends org.gradle.model.RuleSource {
        private static String foo;
    }

    public static class InnerPublicStaticClass extends org.gradle.model.RuleSource {
    }

    public static class HasExplicitDefaultConstructor extends org.gradle.model.RuleSource {
        public HasExplicitDefaultConstructor() {
        }
    }

    public static class HasStaticFinalField extends org.gradle.model.RuleSource {
        private static final Object VALUE = null;
    }
}
