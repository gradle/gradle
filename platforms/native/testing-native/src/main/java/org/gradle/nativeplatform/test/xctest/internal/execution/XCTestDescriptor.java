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

package org.gradle.nativeplatform.test.xctest.internal.execution;

import org.gradle.api.internal.tasks.testing.TestDescriptorInternal;
import org.jspecify.annotations.NullMarked;

import java.util.ArrayList;
import java.util.List;

/**
 * A test descriptor generated from scraping XCTest's output.
 */
@NullMarked
class XCTestDescriptor {
    private final TestDescriptorInternal descriptorInternal;
    private final List<String> messages = new ArrayList<>();

    public XCTestDescriptor(TestDescriptorInternal descriptorInternal) {
        this.descriptorInternal = descriptorInternal;
    }

    public TestDescriptorInternal getDescriptorInternal() {
        return descriptorInternal;
    }

    public List<String> getMessages() {
        return messages;
    }
}
