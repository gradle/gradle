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

    private <T extends Platform> T getDefault(Class<T> type) {
        for (Platform platform: getStore()) {
            if (platform.getName().equals(Platform.DEFAULT_NAME) && type.isInstance(platform)) {
                return (T) platform;
            }
        }
        return null; //TODO freekh: is returning null like this bad?
    }

    public <T extends Platform> List<T> select(final Class<T> type, final List<String> targets) {
        //TODO freekh: consider moving this logic to some other place
        if (targets.isEmpty()) {
            List<T> typed = Lists.newArrayList(withType(type));
            if (typed.size() == 1) { //only one of this type, return that one. Might be the default
                return typed;
            } else if (typed.size() > 1) { //we have more than one, select the best one
                //TODO freekh: for now, we pick the first one defined, but we could pick the best based on the toolchains we have?
                return typed.subList(0, 1);
            }

            //TODO freekh: Change to another type of exception?
            throw new RuntimeException("No platforms is registered for type: " + type); //TODO freekh: this should not happen if the platforms are registered correctly through a plugin, so throw here.

        }

        List<T> selected = new ArrayList<T>();
        for (Platform platform : withType(type)) {
            if (targets.contains(platform.getName())) {
                selected.add((T)platform);
            }
        }
        return selected;
    }

}