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

import com.google.common.collect.Maps;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.api.NamedDomainObjectFactory;
import org.gradle.api.internal.AbstractNamedDomainObjectContainer;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.platform.base.Platform;
import org.gradle.platform.base.PlatformContainer;
import org.gradle.platform.base.PlatformParser;

import java.util.Map;

public class DefaultPlatformContainer extends AbstractNamedDomainObjectContainer<Platform> implements PlatformContainer, NamedDomainObjectContainer<Platform> {
    private Map<PlatformParser, NamedDomainObjectFactory<Platform>> factories = Maps.newHashMap();

    protected DefaultPlatformContainer(Class<Platform> type, Instantiator instantiator) {
        super(type, instantiator);
    }


    @Override
    protected Platform doCreate(String name) {
        for (PlatformParser parser: factories.keySet()) {
            if (parser.parse(name)) {
                return factories.get(parser).create(name);
            }
        }
        throw new InvalidUserDataException(String.format("Cannot create %s because there are no platforms that can parse it. Available platforms: %s", name, factories.keySet())); //TODO: better error reporting


    }

    public void registerPlatform(PlatformParser parser, NamedDomainObjectFactory<Platform> factory) {
        if (factories.containsKey(parser)) {
            throw new RuntimeException("Cannot create factory because parser already exists.");
        }
        factories.put(parser, factory);
    }

}