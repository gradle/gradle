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

package org.gradle.testing.testengine.util;

import java.io.File;

public interface Inputs {
    String PROPERTY_PREFIX = "org.gradle.testing.fixture.testengine.";

    String TEST_RESOURCES_ROOT_DIR_PROP = PROPERTY_PREFIX + "testResourcesRootDir";

    static File getTestResourcesRootDir() {
        String testResourcesRootDir = System.getProperty(TEST_RESOURCES_ROOT_DIR_PROP);
        return new File(testResourcesRootDir);
    }
}
