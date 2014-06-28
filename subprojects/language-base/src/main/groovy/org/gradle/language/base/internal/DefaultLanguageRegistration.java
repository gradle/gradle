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

package org.gradle.language.base.internal;

import org.gradle.language.base.LanguageSourceSet;

public class DefaultLanguageRegistration<U extends LanguageSourceSet, V extends U> implements LanguageRegistration<U, V> {
    private final String name;
    private final Class<U> sourceType;
    private final Class<V> sourceImplementation;

    public DefaultLanguageRegistration(String name, Class<U> sourceType, Class<V> sourceImplementation) {
        this.name = name;
        this.sourceType = sourceType;
        this.sourceImplementation = sourceImplementation;
    }

    public String getName() {
        return name;
    }

    public Class<U> getSourceSetType() {
        return sourceType;
    }

    public Class<V> getSourceSetImplementation() {
        return sourceImplementation;
    }
}
