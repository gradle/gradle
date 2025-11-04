/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.tooling.events.test.source;


import org.gradle.api.Incubating;

/**
 * Representation of the source of a test or container used to navigate to its location by IDEs and build tools.
 * This is a marker interface. Clients need to check instances for concrete subclasses or subinterfaces.
 * <p>
 * This is an API aligned to test sources representation of the JUnit platform.
 *
 * @since 9.4.0
 */
@Incubating
public interface TestSource {
}
