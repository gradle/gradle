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

package org.gradle.platform.base.internal;

import com.google.common.collect.Lists;
import org.gradle.api.*;
import org.gradle.api.specs.Spec;
import org.gradle.util.CollectionUtils;

import java.util.List;

/**
 * A utility that can find a set of named and typed elements in a NamedDomainObjectContainer.
 * @param <T> The type of element required
 */
class NamedElementSelector<T extends Named> implements Transformer<List<T>, NamedDomainObjectContainer<? super T>> {
    private final Class<T> type;
    private final List<String> names;

    NamedElementSelector(Class<T> type, List<String> names) {
        this.type = type;
        this.names = names;
    }

    /**
     * Return the matching elements: never returns an empty list (at this stage this class chooses the default).
     */
    public List<T> transform(NamedDomainObjectContainer<? super T> ts) { //TODO freekh: consider changing to select
        NamedDomainObjectSet<T> allWithType = ts.withType(type);

        if (names.isEmpty()) {
            return Lists.newArrayList(allWithType);
        }

        List<T> matching = Lists.newArrayList();
        final List<String> notFound = Lists.newArrayList(names);
        CollectionUtils.filter(allWithType, matching, new Spec<T>() {
            public boolean isSatisfiedBy(T element) {
                return notFound.remove(element.getName());
            }
        });

        if (notFound.size() == 1) {
            throw new InvalidUserDataException(String.format("Invalid %s: %s", type.getSimpleName(), notFound.get(0)));
        } else if (notFound.size() > 1) {
            throw new InvalidUserDataException(String.format("Invalid %ss: %s", type.getSimpleName(), notFound));
        }
        return matching;
    }
}
