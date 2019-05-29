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
import org.gradle.api.specs.Spec;
import org.gradle.platform.base.Platform;
import org.gradle.platform.base.PlatformContainer;
import org.gradle.util.CollectionUtils;

import java.util.List;

public class DefaultPlatformResolvers implements PlatformResolvers {
    private final List<PlatformResolver<?>> platformResolvers = Lists.newArrayList();
    private final PlatformContainer platforms;

    public DefaultPlatformResolvers(PlatformContainer platforms) {
        this.platforms = platforms;
    }

    @Override
    public void register(PlatformResolver<?> platformResolver) {
        platformResolvers.add(platformResolver);
    }

    @Override
    public <T extends Platform> T resolve(Class<T> type, PlatformRequirement platformRequirement) {
        for (PlatformResolver<?> platformResolver : platformResolvers) {
            if (platformResolver.getType().equals(type)) {
                @SuppressWarnings("unchecked") PlatformResolver<T> pr = (PlatformResolver<T>) platformResolver;
                T resolved = pr.resolve(platformRequirement);
                if (resolved != null) {
                    return resolved;
                }
            }
        }
        return resolveFromContainer(type, platformRequirement);
    }

    private <T extends Platform> T resolveFromContainer(Class<T> type, PlatformRequirement platformRequirement) {
        final String target = platformRequirement.getPlatformName();

        NamedDomainObjectSet<T> allWithType = platforms.withType(type);
        T matching = CollectionUtils.findFirst(allWithType, new Spec<T>() {
            @Override
            public boolean isSatisfiedBy(T element) {
                return element.getName().equals(target);
            }
        });

        if (matching == null) {
            throw new InvalidUserDataException(String.format("Invalid %s: %s", type.getSimpleName(), target));
        }
        return matching;
    }
}
