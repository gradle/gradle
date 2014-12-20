/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.language.base.internal.registry;

import org.gradle.api.NamedDomainObjectFactory;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.language.base.LanguageSourceSet;

abstract public class AbstractLanguageRegistration<U extends LanguageSourceSet> implements LanguageRegistration<U> {

    private Instantiator instantiator;

    public AbstractLanguageRegistration(Instantiator instantiator){
        this.instantiator = instantiator;
    }

    @Override
    public NamedDomainObjectFactory<? extends U> getSourceSetFactory(final String parentName, final FileResolver fileResolver) {
        return new NamedDomainObjectFactory<U>() {
            @Override
            public U create(String name) {
                return instantiator.newInstance(getSourceSetImplementation(), name, parentName, fileResolver);
            }
        };
    }
}
