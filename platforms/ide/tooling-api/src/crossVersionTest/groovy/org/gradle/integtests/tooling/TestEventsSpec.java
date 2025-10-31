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

package org.gradle.integtests.tooling;

import groovy.lang.Closure;
import groovy.lang.DelegatesTo;

/**
 * Spec builder for asserting that a test task emitted the appropriate events.
 */
interface TestEventsSpec {
    void task(String path, @DelegatesTo(value = GroupTestEventSpec.class, strategy = Closure.DELEGATE_FIRST) Closure<?> rootSpec);
}

/**
 * Spec builder for asserting that a test task has events from a group of tests or individual tests within the current group.
 */
interface GroupTestEventSpec extends TestEventSpec {
    /**
     * The name of the group test event.
     */
    void nested(String name, @DelegatesTo(value = GroupTestEventSpec.class, strategy = Closure.DELEGATE_FIRST) Closure<?> spec);

    /**
     * The name of the test in the test event.
     */
    void test(String name, @DelegatesTo(value = TestEventSpec.class, strategy = Closure.DELEGATE_FIRST) Closure<?> spec);

    /**
     * Convenience method for {@link #test(String, Closure)} without any additional configuration.
     */
    void test(String name);
}

/**
 * Expectations for a single test or group.
 */
interface TestEventSpec {
    /**
     * Some output to expect
     */
    void output(String msg);

    /**
     * Some metadata to expect
     */
    void metadata(String key, Object value);

    /**
     * Set the expected test display name
     *
     * This is *not* the operation's display name
     */
    void displayName(String displayName);
}
