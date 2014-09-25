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

import com.google.common.base.Predicate;
import org.gradle.api.internal.DefaultPolymorphicDomainObjectContainer;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.platform.base.Platform;
import org.gradle.platform.base.PlatformContainer;

import java.util.ArrayList;
import java.util.List;

public class DefaultPlatformContainer extends DefaultPolymorphicDomainObjectContainer<Platform> implements PlatformContainer {

    public DefaultPlatformContainer(Class<? extends Platform> type, Instantiator instantiator) {
        super(type, instantiator);
    }

    private <T extends Platform> List<T> select(Predicate<Platform> predicate) {
        List<T> selected = new ArrayList<T>();
        for (Platform platform : getStore()) {
            if (predicate.apply(platform)) {
                selected.add((T)platform);
            }
        }
        return selected;
    }

    public <T extends Platform> List<T> select(final Class<T> type, final List<String> targets) {
        return select(new Predicate<Platform>() {
            public boolean apply(Platform platform) {
                return type.isInstance(platform) && targets.contains(platform.getName());
            }
        });
    }
}