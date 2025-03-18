/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.api.internal.file.delete;

import org.gradle.api.NonExtensible;
import org.gradle.api.internal.provider.PropertyFactory;
import org.gradle.api.provider.Property;

import javax.inject.Inject;

@NonExtensible
public class DefaultDeleteSpec implements DeleteSpecInternal {
    private final Property<Boolean> followSymlinks;
    private Object[] paths;

    @Inject
    public DefaultDeleteSpec(PropertyFactory propertyFactory) {
        this.paths = new Object[0];
        this.followSymlinks = propertyFactory.property(Boolean.class).convention(false);
    }

    @Override
    public Property<Boolean> getFollowSymlinks() {
        return followSymlinks;
    }

    @Override
    public Object[] getPaths() {
        return paths;
    }

    @Override
    public DefaultDeleteSpec delete(Object... files) {
        paths = files;
        return this;
    }
}
