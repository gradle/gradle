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
package org.gradle.api.internal.tasks.testing.junitplatform;

/**
 * This interfaces defines static values used when creating JUnit Platform test descriptors.
 * <p>
 * Changes here affect how tests are displayed in the console.
 */
public interface JUnitPlatformSupport {
    String UNKNOWN_CLASS = "UnknownClass";
    /**
     * An empty string for file-based tests.
     */
    String NON_CLASS = "";
    String UNKNOWN = "Unknown";
}
