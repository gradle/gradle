/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.language.internal;

import org.gradle.api.component.SoftwareComponent;
import org.gradle.api.model.ObjectFactory;

public class DefaultNativeComponentFactory implements NativeComponentFactory {
    private final ObjectFactory objectFactory;

    public DefaultNativeComponentFactory(ObjectFactory objectFactory) {
        this.objectFactory = objectFactory;
    }

    @Override
    public <P extends SoftwareComponent, I extends P> I newInstance(Class<P> publicType, Class<I> implementationType, String name) {
        return objectFactory.newInstance(implementationType, name);
    }
}
