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

package org.gradle.nativeplatform.test.cpp;

import org.gradle.api.provider.Provider;
import org.gradle.language.cpp.CppComponent;
import org.gradle.nativeplatform.test.TestSuiteComponent;

/**
 * A C++ test suite.
 *
 * @since 4.4
 */
public interface CppTestSuite extends CppComponent, TestSuiteComponent {
    /**
     * {@inheritDoc}
     */
    @Override
    Provider<? extends CppTestExecutable> getTestBinary();
}
