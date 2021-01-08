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

package org.gradle.api.internal.tasks.compile.incremental.test;

import java.util.*;

public class SomeClass {

    List<Integer> field = new LinkedList<Integer>();

    private AccessedFromPrivateField accessedFromPrivateField;

    AccessedFromPackagePrivateField someField;

    private AccessedFromPrivateMethod accessedFromPrivateMethod() {
        return null;
    }

    public String accessedFromPrivateMethodBody() {
        return new AccessedFromPrivateMethodBody().toString();
    }

    private Set<String> stuff(HashMap<String, String> map) {
        System.out.println(new Foo());
        return new HashSet<String>();
    }

    private class Foo {
        // Hint: this field won't appear in the ClassAnalysis for SomeClass
        public AccessedFromPrivateClassPublicField anotherField;

        public String toString() {
            return "" + new AccessedFromPrivateClass();
        }
    }
}
