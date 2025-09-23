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

package org.gradle.api.internal.tasks.testing;

import org.jspecify.annotations.NullMarked;

/**
 * A type that consumes tests.
 * <p>
 * Implemented by JUnit and JUnit Platform test frameworks to create types that execute tests by class name.
 */
@NullMarked
public interface TestClassConsumer {
    /**
     * Consumes a class-based test given the class's name.
     *
     * @param testClassName The FQN of the test class to consume
     */
    void consumeClass(String testClassName);
}
