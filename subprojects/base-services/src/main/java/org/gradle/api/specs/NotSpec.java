/*
 * Copyright 2009 the original author or authors.
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
package org.gradle.api.specs;

/**
 * A {@link org.gradle.api.specs.Spec} implementation which negates another {@code Spec}.
 * 
 * @param <T> The target type for this Spec
 */
public class NotSpec<T> implements Spec<T> {
    private Spec<? super T> sourceSpec;

    public NotSpec(Spec<? super T> sourceSpec) {
        this.sourceSpec = sourceSpec;
    }

    public boolean isSatisfiedBy(T element) {
        return !sourceSpec.isSatisfiedBy(element);
    }
}
