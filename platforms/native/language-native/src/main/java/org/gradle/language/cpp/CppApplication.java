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

package org.gradle.language.cpp;

import org.gradle.api.provider.Provider;

/**
 * Configuration for a C++ application, defining the source files that make up the application plus other settings.
 *
 * <p>An instance of this type is added as a project extension by the C++ Application Plugin.</p>
 *
 * @since 4.2
 */
public interface CppApplication extends ProductionCppComponent {
    /**
     * {@inheritDoc}
     */
    @Override
    Provider<? extends CppExecutable> getDevelopmentBinary();
}
