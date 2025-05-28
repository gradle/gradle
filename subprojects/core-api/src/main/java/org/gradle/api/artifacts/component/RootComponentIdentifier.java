/*
 * Copyright 2013 the original author or authors.
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
package org.gradle.api.artifacts.component;

import org.gradle.api.Incubating;

/**
 * An opaque identifier for a component at the root of a dependency graph.
 * <p>
 * The root component owns the root variant, which itself contains all
 * first-level declared dependencies to be resolved.
 *
 * @since 9.0.0
 */
@Incubating
public interface RootComponentIdentifier extends ComponentIdentifier {
}
