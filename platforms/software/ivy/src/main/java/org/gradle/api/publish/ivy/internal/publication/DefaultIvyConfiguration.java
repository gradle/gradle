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

package org.gradle.api.publish.ivy.internal.publication;

import org.gradle.api.publish.ivy.IvyConfiguration;

import java.util.LinkedHashSet;
import java.util.Set;

public class DefaultIvyConfiguration implements IvyConfiguration {
    private final String name;
    private final Set<String> extendsFrom = new LinkedHashSet<String>();

    public DefaultIvyConfiguration(String name) {
        this.name = name;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public void extend(String configuration) {
        extendsFrom.add(configuration);
    }

    @Override
    public Set<String> getExtends() {
        return extendsFrom;
    }
}
