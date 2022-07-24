/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.tooling.internal.protocol.events;

import java.time.Duration;
import java.util.List;

/**
 * DO NOT CHANGE THIS INTERFACE. It is part of the cross-version protocol.
 *
 * @since 5.1
 */
public interface InternalProjectConfigurationResult extends InternalOperationResult {

    /**
     * Returns the results of plugins applied as part of the configuration of this project.
     */
    List<? extends InternalPluginApplicationResult> getPluginApplicationResults();

    /**
     * @since 5.1
     */
    interface InternalPluginApplicationResult {

        /**
         * Returns the identifier of this plugin.
         */
        InternalPluginIdentifier getPlugin();

        /**
         * Returns the total configuration time of this plugin.
         */
        Duration getTotalConfigurationTime();

    }

}
