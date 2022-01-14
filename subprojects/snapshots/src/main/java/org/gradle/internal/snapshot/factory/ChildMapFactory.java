/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.internal.snapshot.factory;

import org.gradle.internal.snapshot.implementation.ChildMapFactoryInternal;
import org.gradle.internal.snapshot.implementation.EmptyChildMap;
import org.gradle.internal.snapshot.implementation.SingletonChildMap;
import org.gradle.internal.snapshot.spi.CaseSensitivity;
import org.gradle.internal.snapshot.spi.ChildMap;

import java.util.Collection;
import java.util.List;

public class ChildMapFactory {
    public static <T> ChildMap<T> emptyChildMap() {
        return EmptyChildMap.getInstance();
    }

    public static <T> ChildMap<T> childMap(String path, T child) {
        return new SingletonChildMap<>(path, child);
    }

    public static <T> ChildMap<T> childMap(CaseSensitivity caseSensitivity, ChildMap.Entry<T> entry1, ChildMap.Entry<T> entry2) {
        return ChildMapFactoryInternal.childMap(caseSensitivity, entry1, entry2);
    }

    public static <T> ChildMap<T> childMap(CaseSensitivity caseSensitivity, Collection<ChildMap.Entry<T>> entries) {
        return ChildMapFactoryInternal.childMap(caseSensitivity, entries);
    }

    public static <T> ChildMap<T> childMapFromSorted(List<ChildMap.Entry<T>> sortedEntries) {
        return ChildMapFactoryInternal.childMapFromSorted(sortedEntries);
    }
}
