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

package org.gradle.api.internal.provider;

import org.gradle.api.internal.provider.support.LazyGroovySupport;
import org.gradle.api.provider.SupportsConvention;
import org.gradle.internal.state.ModelObject;
import org.gradle.internal.state.OwnerAware;

public interface PropertyInternal<T> extends ProviderInternal<T>, HasConfigurableValueInternal, OwnerAware, LazyGroovySupport, SupportsConvention {
    /**
     * Associates this property with the task that produces its value.
     */
    void attachProducer(ModelObject owner);

    /**
     * A simple getter that checks if this property has been finalized.
     *
     * @return {@code true} if this property has been finalized, {@code false} otherwise
     */
    boolean isFinalized();
}
