/*
 * Copyright 2016 the original author or authors.
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
package org.gradle.jvm.internal.resolve;

public interface VariantAxisCompatibility<T> {
    /**
     * Should return true if a variant value is compatible with the requirement.
     * @param requirement the value of a variant, expressing a specific requirement
     * @param value the value of the variant we want to check the compatibility for
     * @return true if the value is compatible with the requirement
     */
    boolean isCompatibleWithRequirement(T requirement, T value);

    /**
     * Should return true if a variant value is a better fit than an old value for a specific requirement.
     * For example, two variant values may be compatible, but one may be a better fit than the other. In
     * that case, we compare two compatible variant values, and must return true if and only if the second
     * one is a better fit than the first one.
     *
     * @param requirement the value of a variant, expressing a specific requirement
     * @param first the first value of a variant
     * @param second another value of the variant
     * @return true if the second value is a better fit than the first one
     */
    boolean betterFit(T requirement, T first, T second);
}
