/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.api.westline;


import org.gradle.api.Action;
import org.gradle.api.Incubating;
import org.gradle.api.provider.Provider;

/**
 * A factory that can create providers of {@link WestlineService} instances.
 *
 * @since 6.1
 */
@Incubating
public interface WestlineServiceFactory {
    /**
     * Creates a provider whose value is an instance of the given service type.
     */
    <T extends WestlineService<P>, P extends WestlineServiceParameters>
    Provider<T> createProviderOf(
        Class<T> serviceType,
        Action<? super WestlineServiceSpec<P>> configuration
    );
}
