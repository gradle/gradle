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
package org.gradle.api.component;

import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;

/**
 * A software component factory is responsible for providing to
 * plugins a way to create software components. Currently the
 * software factory only allows the creation of adhoc software
 * components which can be used for publishing simple components.
 *
 * This is the case whenever there's no component model to be
 * used and that the plugin can solely rely on outgoing variants
 * to publish variants.
 *
 * @since 5.3
 */
@ServiceScope(Scope.Global.class)
public interface SoftwareComponentFactory {
    /**
     * Creates an adhoc software component, which can be used by plugins to
     * build custom component types.
     *
     * @param name the name of the component
     * @return the created component, which must be added to the components container later
     *
     * @since 5.3
     */
    AdhocComponentWithVariants adhoc(String name);
}
