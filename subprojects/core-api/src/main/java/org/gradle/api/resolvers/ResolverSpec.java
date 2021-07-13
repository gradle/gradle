/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.api.resolvers;

import org.gradle.api.Action;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.model.ObjectFactory;

import java.util.ArrayList;
import java.util.List;

public abstract class ResolverSpec {
    protected final ObjectFactory objectFactory;

    private final List<Configuration> extendsFrom = new ArrayList<>();
    private boolean ignoreMissing = false;

    protected ResolverSpec(ObjectFactory objectFactory) {
        this.objectFactory = objectFactory;
    }

    public void from(Configuration configuration) {
        extendsFrom.add(configuration);
    }

    public Configuration[] getFrom() {
        return extendsFrom.toArray(new Configuration[0]);
    }

    public void ignoreMissing() {
        this.ignoreMissing = true;
    }

    public boolean isIgnoreMissing() {
        return ignoreMissing;
    }

    public Action<? super AttributeContainer> getAttributes() {
        return a -> {};
    }
}
