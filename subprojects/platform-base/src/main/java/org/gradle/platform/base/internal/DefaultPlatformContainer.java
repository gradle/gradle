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

package org.gradle.platform.base.internal;

import com.google.common.collect.Lists;
import org.gradle.api.*;
import org.gradle.api.internal.DefaultPolymorphicDomainObjectContainer;
import org.gradle.api.specs.Spec;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.platform.base.Platform;
import org.gradle.platform.base.PlatformContainer;
import org.gradle.util.CollectionUtils;

import java.util.Collections;
import java.util.List;

public class DefaultPlatformContainer extends DefaultPolymorphicDomainObjectContainer<Platform> implements PlatformContainer {

    public DefaultPlatformContainer(Class<? extends Platform> type, Instantiator instantiator) {
        super(type, instantiator);
    }

    public <T extends Platform> List<T> select(final Class<T> type, final List<String> targets) {
        return new NamedElementSelector<T>(type, targets).transform(this);
    }

    /**
     * A utility that can find a set of named and typed elements in a NamedDomainObjectContainer.
     * @param <T> The type of element required
     */
    //TODO freekh: Extract this and unit test the logic (from Daz :-)
    private static class NamedElementSelector<T extends Named> implements Transformer<List<T>, NamedDomainObjectContainer<? super T>> {
        private final Class<T> type;
        private final List<String> names;

        private NamedElementSelector(Class<T> type, List<String> names) {
            this.type = type;
            this.names = names;
        }

        /**
         * Return the matching elements: never returns an empty list (at this stage this class chooses the default).
         */
        public List<T> transform(NamedDomainObjectContainer<? super T> ts) {
            NamedDomainObjectSet<T> allWithType = ts.withType(type);

            //TODO freekh: consider moving this logic to some other place
            if (names.isEmpty()) {
                if (allWithType.size() == 1) {
                    return Lists.newArrayList(allWithType);
                } else if (allWithType.size() > 1) {
                    //TODO freekh: for now, we pick the first one defined, but we could pick the best based on the toolchains we have?
                    // TODO:DAZ This actually selects the first alphabetically
                    return Collections.singletonList(allWithType.iterator().next());
                }

                throw new GradleException(String.format("No element is registered for type: '%s'", type));
            }

            List<T> matching = Lists.newArrayList();
            final List<String> notFound = Lists.newArrayList(names);
            CollectionUtils.filter(allWithType, matching, new Spec<T>() {
                public boolean isSatisfiedBy(T element) {
                    return notFound.remove(element.getName());
                }
            });

            //TODO freekh: create test case for this (Need unit test and integration test for java. Already have one in BinaryNativePlatformIntegrationTest)
            if (notFound.size() == 1) {
                throw new InvalidUserDataException(String.format("Invalid %s: %s", type.getSimpleName(), notFound.get(0)));
            }
            if (notFound.size() > 1) {
                throw new InvalidUserDataException(String.format("Invalid %ss: %s", type.getSimpleName(), notFound));
            }
            return matching;
        }
    }
}