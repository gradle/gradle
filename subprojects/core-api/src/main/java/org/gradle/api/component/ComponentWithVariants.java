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

package org.gradle.api.component;

import java.util.Set;

// getVariants() returns a list of child components.
// This should be called something more like ComponentWithChildren.
//
// When publishing GMM, we will publish all variants of all child components as a "RemoteVariant"
// RemoteVariants are "available-at" variants.

// Presumably this is so you can have different dependencies for different variants,
// and publish them to different maven coordinates, while also having a parent component
// which can reference back to them.

/*

If I had a component C with variants A and B, each with different artifacts, I cannot publish this to a maven repository.
Maven can use scopes to have different dependencies at compile vs runtime, but runtime and compile time must share an artifact.
To publish a component with different variants/artifacts, we spread our multi-variant component over many Maven components.
In a way, each Maven component is acting like a variant. It has an artifact and a dependency set. It also has coordinates -- something a variant does not have.

Kotlin MPP must use this to publish binaries from each target.

Kotlin needs an API to publish components with multiple



 */


/**
 * Represents a {@link SoftwareComponent} that provides one or more mutually exclusive children, or variants.
 *
 * @since 4.3
 */
public interface ComponentWithVariants extends SoftwareComponent {
    Set<? extends SoftwareComponent> getVariants();
}
