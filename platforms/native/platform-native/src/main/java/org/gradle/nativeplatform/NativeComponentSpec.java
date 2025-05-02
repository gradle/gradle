/*
 * Copyright 2013 the original author or authors.
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
package org.gradle.nativeplatform;

import org.gradle.api.Incubating;
import org.gradle.platform.base.ComponentSpec;

/**
 * Definition of a software component that is to be built by Gradle to run a on JVM platform.
 */
@Incubating
public interface NativeComponentSpec extends ComponentSpec {
    /**
     * The name that is used to construct the output file names when building this component.
     */
    String getBaseName();

    /**
     * Sets the name that is used to construct the output file names when building this component.
     */
    void setBaseName(String baseName);
}
