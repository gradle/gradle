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

package org.gradle.nativecode.base.internal;

import org.gradle.api.internal.AbstractNamedDomainObjectContainer;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.nativecode.base.Flavor;
import org.gradle.nativecode.base.FlavorContainer;

import java.util.Collection;

public class DefaultFlavorContainer extends AbstractNamedDomainObjectContainer<Flavor> implements FlavorContainer {
    boolean hasDefault;
    public DefaultFlavorContainer(Instantiator instantiator) {
        super(Flavor.class, instantiator);
        add(Flavor.DEFAULT);
        hasDefault = true;
    }

    @Override
    public boolean add(Flavor o) {
        removeDefault();
        return super.add(o);
    }

    @Override
    public boolean addAll(Collection<? extends Flavor> c) {
        removeDefault();
        return super.addAll(c);
    }

    private void removeDefault() {
        if (hasDefault) {
            remove(Flavor.DEFAULT);
            hasDefault = false;
        }
    }

    @Override
    protected Flavor doCreate(String name) {
        return getInstantiator().newInstance(DefaultFlavor.class, name);
    }
}
