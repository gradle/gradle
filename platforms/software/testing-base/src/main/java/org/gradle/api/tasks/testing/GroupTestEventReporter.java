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
 * Reports test events, and child test event reporters.
 *
 * @since 8.12
 */
@Incubating
public interface GroupTestEventReporter extends TestEventReporter {
    /**
     * Create a child 'test' test event reporter. This can be used, for example, to emit events for each method in a tested class.
     *
     * <p>
     * Since this is a 'test' node, it will not have any children. To add children, use {@link #reportTestGroup(String)}.
     * </p>
     *
     * @param name the name of the test
     * @param displayName the display name of the test
     * @return the nested test event reporter
     * @since 8.12
     */
    TestEventReporter reportTest(String name, String displayName);

    /**
     * Create a child group test event reporter. This can be used, for example, to emit events for a tested class, or a parameterized test method (but not the individual parameterized tests).
     *
     * <p>
     * Since this is a group, it can have children. To add a 'test' node, use {@link #reportTest(String, String)}.
     * </p>
     *
     * @param name the name of the composite, which will also be used as the display name
     * @return the nested test event reporter
     * @since 8.12
     */
    GroupTestEventReporter reportTestGroup(String name);
}
