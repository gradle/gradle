/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.model.internal.manage.schema.extract;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Collection;

public class PropertyAccessorExtractionContext {
    private final Collection<Method> declaringMethods;
    private final Method mostSpecificDeclaration;
    private final boolean declaredInManagedType;
    private final boolean declaredAsAbstract;

    public PropertyAccessorExtractionContext(Collection<Method> declaringMethods) {
        this(declaringMethods, ModelSchemaUtils.isMethodDeclaredInManagedType(declaringMethods));
    }

    public PropertyAccessorExtractionContext(Collection<Method> declaringMethods, boolean declaredInManagedType) {
        this.declaringMethods = declaringMethods;
        this.mostSpecificDeclaration = declaringMethods.iterator().next();
        this.declaredInManagedType = declaredInManagedType;
        this.declaredAsAbstract = Modifier.isAbstract(this.mostSpecificDeclaration.getModifiers());
    }

    public Collection<Method> getDeclaringMethods() {
        return declaringMethods;
    }

    public Method getMostSpecificDeclaration() {
        return mostSpecificDeclaration;
    }

    public boolean isDeclaredInManagedType() {
        return declaredInManagedType;
    }

    public boolean isDeclaredAsAbstract() {
        return declaredAsAbstract;
    }
}
