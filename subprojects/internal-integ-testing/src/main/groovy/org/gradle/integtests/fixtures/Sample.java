/*
 * Copyright 2010 the original author or authors.
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

package org.gradle.integtests.fixtures;

import com.google.common.annotations.VisibleForTesting;
import org.gradle.integtests.fixtures.executer.IntegrationTestBuildContext;
import org.gradle.test.fixtures.dsl.GradleDsl;
import org.gradle.test.fixtures.file.TestDirectoryProvider;
import org.gradle.test.fixtures.file.TestFile;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;

/**
 * A Junit rule which copies a sample into the test directory before the test executes. Looks for a
 * {@link org.gradle.integtests.fixtures.UsesSample} annotation on the test method to determine which sample the
 * test requires. If not found, uses the default sample provided in the constructor.
 */
public class Sample implements TestRule {
    private final Logger logger = LoggerFactory.getLogger(Sample.class);
    private final String defaultSampleName;
    private final String testSampleDirName;

    private String sampleName;
    private TestFile sampleDir;
    private TestDirectoryProvider testDirectoryProvider;

    public Sample(TestDirectoryProvider testDirectoryProvider) {
        this(testDirectoryProvider, null);
    }

    public Sample(TestDirectoryProvider testDirectoryProvider, String defaultSampleName) {
        this(testDirectoryProvider, defaultSampleName, null);
    }

    public Sample(TestDirectoryProvider testDirectoryProvider, String defaultSampleName, String testSampleDirName) {
        this.testDirectoryProvider = testDirectoryProvider;
        this.defaultSampleName = defaultSampleName;
        this.testSampleDirName = testSampleDirName;
    }

    @Override
    public Statement apply(final Statement base, Description description) {
        sampleName = getSampleName(description);
        return new Statement() {
            @Override
            public void evaluate() throws Throwable {
                if (sampleName != null) {
                    IntegrationTestBuildContext context = IntegrationTestBuildContext.INSTANCE;
                    TestFile srcDir = context.getSamplesDir().file(sampleName);
                    logger.debug("Copying sample '{}' to test directory.", sampleName);
                    srcDir.copyTo(getDir());
                } else {
                    logger.debug("No sample specified for this test, skipping.");
                }
                base.evaluate();
            }
        };
    }

    private String getSampleName(Description description) {
        UsesSample annotation = description.getAnnotation(UsesSample.class);
        return annotation != null
            ? annotation.value()
            : defaultSampleName;
    }

    public TestFile getDir() {
        if (sampleDir == null) {
            sampleDir = computeSampleDir();
        }
        return sampleDir;
    }

    @Nullable
    private TestFile computeSampleDir() {
        if (testSampleDirName != null) {
            return testFile(testSampleDirName);
        }
        if (sampleName != null) {
            return testFile(dirNameFor(sampleName));
        }
        return null;
    }

    /**
     * Shortens path as much as possible to prevent long path issues on Windows
     * by keeping the last segment only, ignoring /groovy or /kotlin suffix.
     */
    @VisibleForTesting
    static String dirNameFor(String sampleName) {
        String dirName = sampleName.endsWith("/") ? sampleName.substring(0, sampleName.length() - 1) : sampleName;
        for (String dslLanguageCodeName : GradleDsl.languageCodeNames()) {
            String dslPathFragment = '/' + dslLanguageCodeName;
            if (dirName.endsWith(dslPathFragment)) {
                dirName = dirName.substring(0, dirName.lastIndexOf(dslPathFragment));
            }
        }
        return dirName.substring(dirName.lastIndexOf('/') + 1);
    }

    private TestFile testFile(String testSampleDirName) {
        return testDirectoryProvider.getTestDirectory().file(testSampleDirName);
    }
}
