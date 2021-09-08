/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.api.provider;

import org.gradle.api.Incubating;
import org.gradle.api.NonExtensible;
import org.gradle.internal.HasInternalProtocol;

/**
 * An object that can be converted to a {@link Provider}.
 *
 * @param <T> Type of value represented by provider
 * @since 7.3
 */
@Incubating
@HasInternalProtocol
@NonExtensible
public interface ProviderConvertible<T> {

    /**
     * Returns a {@link Provider} from this object.
     */
    Provider<T> asProvider();

}
