/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.tooling.internal.adapter;

import java.util.List;
import java.util.Map;
import java.util.Set;

public class TypeInspectorTestHelper {
    interface Item1 {
    }

    interface Item2 {
    }

    interface Item3 {
    }

    interface Item4 {
    }

    interface Item5 {
    }

    interface SuperThing {
        Runnable method1();

        List<Item1> method2();

        Map<Set<Item2>, String> method3();
    }

    interface GenericThing {
        Item3[] method1(String p);
        List<Item5>[] method2(String p);
    }

    interface Thing extends SuperThing, GenericThing {
        <T extends Item4> List<T> get();
    }

    interface Parent {
        Child getFirstChild();
    }

    interface Child {
        Parent getParent();
        Child getNextSibling();
    }

    interface GenericItem1<T> {
    }

    interface GenericItem2<T> {
    }

    interface  GenericChild<T extends GenericItem1<? extends T>> extends GenericItem1<T> {
        <U extends GenericItem2<? super U>> void method(GenericItem2<U> p);
    }
}
