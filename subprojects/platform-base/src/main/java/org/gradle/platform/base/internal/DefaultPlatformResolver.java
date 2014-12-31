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
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.NamedDomainObjectSet;
import org.gradle.api.Transformer;
import org.gradle.api.specs.Spec;
import org.gradle.platform.base.Platform;
import org.gradle.platform.base.PlatformContainer;
import org.gradle.util.CollectionUtils;

import java.util.List;

public class DefaultPlatformResolver implements PlatformResolver {

    private final PlatformContainer platforms;

    public DefaultPlatformResolver(PlatformContainer platforms) {
        this.platforms = platforms;
    }

    public <T extends Platform> List<T> resolve(Class<T> type, List<PlatformRequirement> requirements) {
        List<String> targets = CollectionUtils.collect(requirements, new Transformer<String, PlatformRequirement>() {
            @Override
            public String transform(PlatformRequirement platformRequirement) {
                return platformRequirement.getPlatformName();
            }
        });

        NamedDomainObjectSet<T> allWithType = platforms.withType(type);

        List<T> matching = Lists.newArrayList();
        final List<String> notFound = Lists.newArrayList(targets);
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