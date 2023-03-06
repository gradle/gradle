/*
 * Copyright 2023 the original author or authors.
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

import java.util.regex.Pattern;

/**
 * Represents a module loaded from the gradle distribution, including metadata detailing the name of the JAR
 * which implements the module and an example class name, which if loaded, signals the existence of the module.
 *
 * <p>The complexity here is necessary to determine if test framework dependencies are already present on the
 * application classpath, and thus do not need to be loaded from the Gradle distribution. The behavior of loading
 * test framework dependencies from the Gradle distribution is deprecated and will be removed in 9.0</p>
 */
public class TestFrameworkDistributionModule {

    private final String moduleName;
    private final Pattern jarFilePattern;
    private final String exampleClassName;

    public TestFrameworkDistributionModule(String moduleName, Pattern jarFilePattern, String exampleClassName) {
        this.moduleName = moduleName;
        this.jarFilePattern = jarFilePattern;
        this.exampleClassName = exampleClassName;
    }

    public String getModuleName() {
        return moduleName;
    }

    public Pattern getJarFilePattern() {
        return jarFilePattern;
    }

    public String getExampleClassName() {
        return exampleClassName;
    }
}
