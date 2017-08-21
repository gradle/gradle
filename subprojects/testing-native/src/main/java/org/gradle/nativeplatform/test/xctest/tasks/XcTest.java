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

import org.gradle.api.Incubating;
import org.gradle.api.file.RegularFile;
import org.gradle.api.file.RegularFileVar;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.internal.tasks.testing.detection.TestExecuter;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.testing.AbstractTestTask;
import org.gradle.nativeplatform.test.xctest.internal.NativeTestExecuter;
import org.gradle.process.ProcessForkOptions;
import org.gradle.process.internal.DefaultProcessForkOptions;

import javax.inject.Inject;
import java.io.File;

/**
 * Executes XCTest tests. Test are always run in a single execution.
 *
 * @since 4.2
 */
@Incubating
public class XcTest extends AbstractTestTask<XcTest> {
    private final RegularFileVar testBundleDir;

    @Inject
    public XcTest() {
        super(XcTest.class);
        this.testBundleDir = newInputFile();
    }

    @Inject
    protected FileResolver getFileResolver() {
        throw new UnsupportedOperationException();
    }

    @Override
    protected ProcessForkOptions createForkOptions() {
        return new DefaultProcessForkOptions(getFileResolver());
    }

    @Override
    protected TestExecuter<XcTest> createTestExecuter() {
        return getObjectFactory().newInstance(NativeTestExecuter.class);
    }

    @Override
    protected String createNoMatchingTestErrorMessage() {
        return "";
    }

    @InputDirectory
    public File getTestBundleDir() {
        return testBundleDir.getAsFile().getOrNull();
    }

    public void setTestBundleDir(File testBundleDir) {
        this.testBundleDir.set(testBundleDir);
    }

    public void setTestBundleDir(Provider<? extends RegularFile> testBundleDir) {
        this.testBundleDir.set(testBundleDir);
    }
}
