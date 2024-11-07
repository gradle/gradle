/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.api.tasks.testing;

import org.gradle.api.Incubating;

/**
 * Generates test events, and child test event generators.
 *
 * @since 8.12
 */
@Incubating
public interface CompositeTestEventGenerator extends TestEventGenerator {
    /**
     * Create an atomic node test event generator. This can be used, for example, to add a node for each method in a tested class.
     *
     * <p>
     * Since this is a atomic node, it will not have any children. To add children, use {@link #createCompositeNode(String)}.
     * </p>
     *
     * @param name the name of the node
     * @param displayName the display name of the node
     * @return the nested test event generator
     * @since 8.12
     */
    TestEventGenerator createAtomicNode(String name, String displayName);

    /**
     * Create a nested composite test event generator. This can be used, for example, to add a node for a tested class.
     *
     * <p>
     * Since this is a composite node, it can have children. To add a solitary node, use {@link #createAtomicNode(String, String)}.
     * </p>
     *
     * @param name the name of the node
     * @return the nested test event generator
     * @since 8.12
     */
    CompositeTestEventGenerator createCompositeNode(String name);
}
