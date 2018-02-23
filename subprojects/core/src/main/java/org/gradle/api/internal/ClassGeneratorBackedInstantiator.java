/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.api.internal;

import org.gradle.internal.reflect.Instantiator;

public class ClassGeneratorBackedInstantiator implements Instantiator {
    private final ClassGenerator classGenerator;
    private final Instantiator instantiator;

    public ClassGeneratorBackedInstantiator(ClassGenerator classGenerator, Instantiator instantiator) {
        this.classGenerator = classGenerator;
        this.instantiator = instantiator;
    }

    public <T> T newInstance(Class<? extends T> type, Object... parameters) {
        // During the construction of the object, it will look for this global instantiator.
        // This is to support ExtensionContainer.add(String, Class, Object...) which facilitates
        // making extensions ExtensionAware themselves.
        // See: AsmBackedClassGenerator.MixInExtensibleDynamicObject#getInstantiator()
        ThreadGlobalInstantiator.set(this);
        try {
            return instantiator.newInstance(classGenerator.generate(type), parameters);    
        } finally {
            ThreadGlobalInstantiator.set(null);
        }
        
    }
}
