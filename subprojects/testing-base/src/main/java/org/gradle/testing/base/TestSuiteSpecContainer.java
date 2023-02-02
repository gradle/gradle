/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.testing.base;

import org.gradle.api.Incubating;
import org.gradle.model.ModelMap;

/**
 * A container of {@link TestSuiteSpec} instances.
 *
 * This class is actually much older than 8.1, but 8.1 renamed it from TestSuiteContainer,
 * in order to free up that name for something more useful.
 *
 * @since 8.1
 */
@Incubating
public interface TestSuiteSpecContainer extends ModelMap<TestSuiteSpec> {
}
