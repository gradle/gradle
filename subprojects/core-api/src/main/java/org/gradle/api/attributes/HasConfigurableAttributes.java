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

package org.gradle.api.attributes;

import org.gradle.api.Action;
import org.gradle.api.Incubating;

/**
 * Represents something that carries attributes by utilizing an
 * {@link AttributeContainer} that is configurable.
 *
 * @param <SELF> type extending this interface
 *
 * @since 3.4
 */
@Incubating
public interface HasConfigurableAttributes<SELF> extends HasAttributes {

    /**
     * Configure the attribute container that provides the attributes
     * associated with this domain object.
     */
    @Incubating
    SELF attributes(Action<? super AttributeContainer> action);
}
