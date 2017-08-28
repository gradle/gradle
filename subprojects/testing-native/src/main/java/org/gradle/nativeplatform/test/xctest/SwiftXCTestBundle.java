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

package org.gradle.nativeplatform.test.xctest;

import org.gradle.api.Incubating;
import org.gradle.api.file.RegularFileVar;
import org.gradle.language.swift.SwiftComponent;

/**
 * Configuration for a Swift XCTest Bundle, defining the source files that make up the bundle plus other settings.
 *
 * <p>An instance of this type is added as a project extension by the Swift XCTest plugin.
 *
 * @since 4.3
 */
@Incubating
public interface SwiftXCTestBundle extends SwiftComponent {
    /**
     * Defines the location of Info.plist.
     *
     * <p>{@code src/test/resources/Info.plist} is used by default.</p>
     */
    RegularFileVar getInformationPropertyList();
}
