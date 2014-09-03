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
package org.gradle.language.base.internal;

import org.gradle.language.base.FunctionalSourceSet;
import org.gradle.language.base.LanguageSourceSet;
import org.gradle.api.internal.DefaultPolymorphicDomainObjectContainer;
import org.gradle.internal.reflect.Instantiator;

public class DefaultFunctionalSourceSet extends DefaultPolymorphicDomainObjectContainer<LanguageSourceSet> implements FunctionalSourceSet {
    private final String name;

    public DefaultFunctionalSourceSet(String name, Instantiator instantiator) {
        super(LanguageSourceSet.class, instantiator);
        this.name = name;
    }

    @Override
    public String toString() {
        return String.format("source set '%s'", name);
    }

    public String getName() {
        return name;
    }
}
