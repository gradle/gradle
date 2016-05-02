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
package org.gradle.nativeplatform.internal;

import org.gradle.api.specs.Spec;
import org.gradle.nativeplatform.platform.NativePlatform;
import org.gradle.nativeplatform.platform.internal.NativePlatforms;
import org.gradle.platform.base.internal.PlatformRequirement;
import org.gradle.platform.base.internal.PlatformResolver;
import org.gradle.util.CollectionUtils;

public class NativePlatformResolver implements PlatformResolver<NativePlatform> {
    private final NativePlatforms nativePlatforms;

    public NativePlatformResolver(NativePlatforms nativePlatforms) {
        this.nativePlatforms = nativePlatforms;
    }

    @Override
    public Class<NativePlatform> getType() {
        return NativePlatform.class;
    }

    @Override
    public NativePlatform resolve(final PlatformRequirement platformRequirement) {
        return CollectionUtils.findFirst(nativePlatforms.defaultPlatformDefinitions(), new Spec<NativePlatform>() {
            @Override
            public boolean isSatisfiedBy(NativePlatform element) {
                return element.getName().equals(platformRequirement.getPlatformName());
            }
        });
    }
}
