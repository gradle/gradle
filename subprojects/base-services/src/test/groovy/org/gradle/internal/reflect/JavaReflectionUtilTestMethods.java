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

package org.gradle.internal.reflect;

import java.util.Collection;
import java.util.List;
import java.util.function.BiConsumer;

public class JavaReflectionUtilTestMethods {
    private <T> T simpleGenericReturnType() { return null; }

    private <T> List<T> encapsulatedTypeVariable() { return null; }

    private <T> T[] arrayTypeWithTypeVariable() { return null; }

    private <T> List<Collection<? extends List<? super T>>[]> complexTypeWithTypeVariable() { return null; }

    private <T> List<BiConsumer<Collection<? super Class<Integer>>, ? extends List<? extends T>>[]> anotherComplexTypeWithTypeVariable() { return null; }
    private <T> List<BiConsumer<Collection<? super Class<Integer>>, ? extends List<T[]>>> complexTypeWithArrayTypeVariable() { return null; }
}
