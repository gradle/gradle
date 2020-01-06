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

import org.gradle.api.file.Directory;
import org.gradle.api.file.RegularFile;
import org.gradle.api.provider.Provider;
import org.gradle.language.swift.SwiftBinary;
import org.gradle.nativeplatform.tasks.AbstractLinkTask;
import org.gradle.nativeplatform.test.TestComponent;
import org.gradle.nativeplatform.test.xctest.tasks.XCTest;

/**
 * An test binary built from Swift source.
 *
 * @since 4.4
 */
public interface SwiftXCTestBinary extends SwiftBinary, TestComponent {
    /**
     * Returns the executable test file for this binary.
     *
     * @since 4.5
     */
    Provider<RegularFile> getExecutableFile();

    /**
     * Returns the installation directory for this binary.
     *
     * @since 4.4
     */
    Provider<Directory> getInstallDirectory();

    /**
     * Returns the script for running this binary.
     *
     * @since 4.4
     */
    Provider<RegularFile> getRunScriptFile();

    /**
     * Returns the link task for this binary.
     *
     * @since 4.5
     */
    Provider<? extends AbstractLinkTask> getLinkTask();

    /**
     * {@inheritDoc}
     */
    @Override
    Provider<? extends XCTest> getRunTask();
}
