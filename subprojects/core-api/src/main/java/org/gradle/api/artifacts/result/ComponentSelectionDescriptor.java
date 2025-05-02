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
package org.gradle.api.artifacts.result;

import org.gradle.internal.HasInternalProtocol;
import org.gradle.internal.scan.UsedByScanPlugin;

/**
 * A component selection description, which wraps a cause with an optional custom description.
 *
 * @since 4.6
 */
@UsedByScanPlugin
@HasInternalProtocol
public interface ComponentSelectionDescriptor {

    /**
     * Returns the cause associated with this descriptor
     *
     * @return a component selection cause
     */
    ComponentSelectionCause getCause();

    /**
     * Returns a description for the selection. This may be the default description of a {@link ComponentSelectionCause cause},
     * or a custom description provided typically through a rule.
     *
     * @return the description of this component selection
     */
    String getDescription();

}
