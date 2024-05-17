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

package org.gradle.platform.base.component.internal;

import org.gradle.platform.base.ComponentSpec;
import org.gradle.platform.base.internal.ComponentSpecIdentifier;
import org.gradle.platform.base.internal.ComponentSpecInternal;

public class AbstractComponentSpec implements ComponentSpec, ComponentSpecInternal {
    private final ComponentSpecIdentifier identifier;
    private final Class<?> publicType;

    public AbstractComponentSpec(ComponentSpecIdentifier identifier, Class<?> publicType) {
        this.publicType = publicType;
        this.identifier = identifier;
    }

    @Override
    public ComponentSpecIdentifier getIdentifier() {
        return identifier;
    }

    @Override
    public String getName() {
        return identifier.getName();
    }

    @Override
    public String getProjectPath() {
        return identifier.getProjectPath();
    }

    protected String getTypeName() {
        return publicType.getSimpleName();
    }

    @Override
    public String getDisplayName() {
        return getTypeName() + " '" + identifier.getPath() + "'";
    }

    @Override
    public String toString() {
        return getDisplayName();
    }
}
