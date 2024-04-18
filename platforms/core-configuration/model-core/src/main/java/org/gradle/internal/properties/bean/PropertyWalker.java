/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.internal.properties.bean;

import org.gradle.internal.properties.PropertyVisitor;
import org.gradle.internal.reflect.validation.TypeValidationContext;
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;

/**
 * Walks properties declared by the type.
 */
@ServiceScope(Scope.Global.class)
public interface PropertyWalker {
    void visitProperties(Object instance, TypeValidationContext validationContext, PropertyVisitor visitor);
}
