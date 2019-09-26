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

package org.gradle.nativeplatform.test.xctest.tasks;

import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFile;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.internal.tasks.testing.TestExecuter;
import org.gradle.api.internal.tasks.testing.filter.DefaultTestFilter;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.SkipWhenEmpty;
import org.gradle.api.tasks.testing.AbstractTestTask;
import org.gradle.nativeplatform.test.xctest.internal.execution.XCTestExecuter;
import org.gradle.nativeplatform.test.xctest.internal.execution.XCTestSelection;
import org.gradle.nativeplatform.test.xctest.internal.execution.XCTestTestExecutionSpec;

import javax.annotation.Nullable;
import java.io.File;
import java.util.List;

/**
 * Executes XCTest tests. Test are always run in a single execution.
 *
 * @since 4.5
 */
public class XCTest extends AbstractTestTask {
    private final DirectoryProperty workingDirectory;
    private final DirectoryProperty testInstallDirectory;
    private final RegularFileProperty runScriptFile;

    public XCTest() {
        ObjectFactory objectFactory = getProject().getObjects();
        workingDirectory = objectFactory.directoryProperty();
        testInstallDirectory = objectFactory.directoryProperty();
        runScriptFile = objectFactory.fileProperty();
    }

    @Override
    protected XCTestTestExecutionSpec createTestExecutionSpec() {
        DefaultTestFilter testFilter = (DefaultTestFilter) getFilter();

        return new XCTestTestExecutionSpec(workingDirectory.getAsFile().get(), runScriptFile.getAsFile().get(), getPath(),
            new XCTestSelection(testFilter.getIncludePatterns(), testFilter.getCommandLineIncludePatterns()));
    }

    /**
     * Sets the test suite bundle or executable location
     */
    @PathSensitive(PathSensitivity.RELATIVE)
    @InputDirectory
    public DirectoryProperty getTestInstallDirectory() {
        return testInstallDirectory;
    }

    /**
     * Returns test suite bundle or executable location
     */
    @Internal("Covered by getRunScript")
    public RegularFileProperty getRunScriptFile() {
        return runScriptFile;
    }

    /**
     * Returns the working directory property for this test.
     */
    @Internal
    public DirectoryProperty getWorkingDirectory() {
        return workingDirectory;
    }

    @Override
    protected TestExecuter<XCTestTestExecutionSpec> createTestExecuter() {
        return getProject().getObjects().newInstance(XCTestExecuter.class);
    }

    /**
     * Workaround for when the task is given an input file that doesn't exist
     */
    @SkipWhenEmpty
    @Nullable
    @Optional
    @PathSensitive(PathSensitivity.ABSOLUTE)
    @InputFile
    protected File getRunScript() {
        RegularFile runScript = getRunScriptFile().get();
        File runScriptFile = runScript.getAsFile();
        if (!runScriptFile.exists()) {
            return null;
        }
        return runScriptFile;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public XCTest setTestNameIncludePatterns(List<String> testNamePattern) {
        super.setTestNameIncludePatterns(testNamePattern);
        return this;
    }
}
