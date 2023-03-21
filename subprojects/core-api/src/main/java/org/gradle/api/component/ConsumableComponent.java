/*
 * Copyright 2023 the original author or authors.
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

import org.gradle.api.Incubating;

import java.util.Set;

/**
 * A {@link SoftwareComponent} which participates in dependency resolution and can be published
 * to external repositories. Consumable components encapsulate all logic and domain objects required
 * to produce software products, and exposes them via variants.
 *
 * <p>Within a dependency graph, a selected component may contribute multiple variants as long as
 * those variants have distinct capabilities.</p>
 *
 * @since 8.2
 */
@Incubating
public interface ConsumableComponent extends SoftwareComponent {

    // TODO: A local component belongs to a module and should define a ComponentIdentifier which
    // defines a module name and group. A ModuleComponentIdentifier immediately seems fitting, however
    // it defines a version, which may not make sense for local components. Local components really have
    // an implicit "latest" version, and it only makes sense to have a version when the component is
    // serialized during publication. Further, traditional project components have a ProjectComponentIdentifier
    // and most of the Gradle infrastructure assumes this. Exposing a ModuleComponentIdentifier may not
    // make the most sense here.

    /**
     * The variants of this component.
     */
    Set<? extends ConsumableVariant> getVariants();

}
