/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.api.internal.provider;

import org.gradle.api.provider.HasMultipleValues;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.SetProperty;

@SuppressWarnings("unused") // registered as Groovy extension in ExtensionModule
public final class CollectionPropertyExtensions {

    private CollectionPropertyExtensions() {}

    /**
     * Adds an element to the property value.
     *
     * <p>Extension method to support the {@code << } operator in Groovy.</p>
     *
     * @param self the {@link org.gradle.api.provider.ListProperty} or the {@link org.gradle.api.provider.SetProperty}
     * @param element the element
     * @return self
     */
    public static <T> HasMultipleValues<T> leftShift(HasMultipleValues<T> self, T element) {
        self.add(element);
        return self;
    }

    /**
     * Adds a provider of the element to the property value.
     *
     * <p>Extension method to support the {@code << } operator in Groovy.</p>
     *
     * @param self the {@link org.gradle.api.provider.ListProperty} or the {@link org.gradle.api.provider.SetProperty}
     * @param provider the provider of the element
     * @return self
     */
    public static <T> HasMultipleValues<T> leftShift(HasMultipleValues<T> self, Provider<? extends T> provider) {
        self.add(provider);
        return self;
    }

    /**
     * Creates a stand-in for {@link ListProperty} or {@link SetProperty} to be used as a left-hand side operand of {@code +=}.
     * <p>
     * The AST transformer knows the name of this method.
     *
     * @param lhs the property
     * @param <T> the type of property elements, to help the static type checker
     * @return the stand-in object to call {@code plus} on
     * @see org.gradle.api.internal.groovy.support.CompoundAssignmentTransformer
     */
    public static <T> CollectionPropertyCompoundAssignmentStandIn<T> forCompoundAssignment(HasMultipleValues<T> lhs) {
        return ((AbstractCollectionProperty<T, ?>) lhs).forCompoundAssignment();
    }
}
