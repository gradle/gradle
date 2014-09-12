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

package org.gradle.nativeplatform.platform.internal;

import org.gradle.api.internal.AbstractNamedDomainObjectContainer;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.nativeplatform.platform.NativePlatform;
import org.gradle.nativeplatform.platform.PlatformContainer;

public class DefaultPlatformContainer extends AbstractNamedDomainObjectContainer<NativePlatform> implements PlatformContainer {
    public DefaultPlatformContainer(Instantiator instantiator) {
        super(NativePlatform.class, instantiator);
    }

    @Override
    protected NativePlatform doCreate(String name) {
        return getInstantiator().newInstance(DefaultNativePlatform.class, name);
    }
}
