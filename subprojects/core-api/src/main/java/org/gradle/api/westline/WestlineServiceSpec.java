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

/**
 * A definition for a {@link WestlineService}.
 *
 * @param <P> the parameter type for the service.
 * @since 6.1
 */
@Incubating
public interface WestlineServiceSpec<P extends WestlineServiceParameters> {
    /**
     * Returns the parameters to be used to create the service instance.
     */
    P getParameters();

    /**
     * Runs the given action against the parameters, to configure the parameters.
     */
    void parameters(Action<? super P> action);
}
