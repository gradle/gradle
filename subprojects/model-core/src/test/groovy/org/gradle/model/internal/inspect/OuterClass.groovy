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

package org.gradle.model.internal.inspect

class OuterClass {

    abstract static class AbstractClass {}

    static interface AnInterface {}

    class InnerInstanceClass {}

    private class PrivateInnerStaticClass {}

    static class HasSuperclass extends InnerPublicStaticClass {}

    static class HasTwoConstructors {
        HasTwoConstructors() {
        }

        HasTwoConstructors(String arg) {
        }
    }

    static class HasInstanceVar {
        String foo
    }

    static class HasFinalInstanceVar {
        final String foo = null
    }

    static class HasNonFinalStaticVar {
        static String foo = null
    }

    static class InnerPublicStaticClass {}

    static class HasExplicitDefaultConstructor {
        HasExplicitDefaultConstructor() {
        }
    }

    static class HasStaticFinalField {
        static final VALUE = null
    }

}
