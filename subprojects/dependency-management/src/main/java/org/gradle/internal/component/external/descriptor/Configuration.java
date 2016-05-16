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

package org.gradle.internal.component.external.descriptor;

import org.gradle.util.CollectionUtils;

import java.util.Collection;
import java.util.List;

public class Configuration {
    private final String name;
    private final boolean transitive;
    private final boolean visible;
    private final List<String> extendsFrom;

    public Configuration(String name, boolean transitive, boolean visible, Collection<String> extendsFrom) {
        this.name = name;
        this.transitive = transitive;
        this.visible = visible;
        this.extendsFrom = CollectionUtils.toList(extendsFrom);
    }

    public String getName() {
        return name;
    }

    public boolean isTransitive() {
        return transitive;
    }

    public boolean isVisible() {
        return visible;
    }

    public List<String> getExtendsFrom() {
        return extendsFrom;
    }
}
