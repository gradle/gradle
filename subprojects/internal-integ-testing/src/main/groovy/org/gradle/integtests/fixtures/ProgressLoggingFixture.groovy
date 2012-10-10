/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.integtests.fixtures

import org.apache.commons.lang.RandomStringUtils
import org.gradle.util.TestFile
import org.junit.rules.MethodRule
import org.junit.runners.model.FrameworkMethod
import org.junit.runners.model.Statement

class ProgressLoggingFixture implements MethodRule {

    private static final String FILE_TRANSFER_PATTERN = /\[START (Download|Upload).*]/

    private TestFile loggingOutputFile = null

    void withProgressLogging(TestFile... testFiles) {
        writeSomeContent(testFiles)

    }

    def writeSomeContent(TestFile[] testFiles) {
        for (TestFile testFile : testFiles) {
            if (!testFile.exists()) {
                testFile.createFile()
            }
            testFile.text = RandomStringUtils.random(2 * 1024)
        }
    }

    boolean downloadProgressLogged(String url) {
        return progressLogged("Download", url)
    }

    boolean uploadProgressLogged(String url) {
        return progressLogged("Upload", url)
    }

    private boolean progressLogged(String operation, String url) {
        def lines = loggingOutputFile.exists() ? loggingOutputFile.text.readLines() : []
        def startIndex = lines.indexOf("[START " + operation + " " + url + "]")
        if (startIndex == -1) {
            return false
        }
        lines = lines[startIndex..<lines.size()]
        lines = lines[0..lines.indexOf("[END " + operation + " " + url + "]")]
        lines.size() >= 2
    }

    void resetExpectations() {
        if (loggingOutputFile != null && loggingOutputFile.exists()) {
            loggingOutputFile.text = ""
        }
    }

    Statement apply(Statement base, FrameworkMethod method, Object target) {
        TestFile initFile
        GradleDistributionExecuter executer = RuleHelper.getField(target, GradleDistributionExecuter)
        GradleDistribution distribution = RuleHelper.getField(target, GradleDistribution)
        TestFile temporaryFolder = distribution.getTemporaryFolder().getDir()
        loggingOutputFile = temporaryFolder.file("loggingoutput.log")
        initFile = temporaryFolder.file("init.gradle")
        initFile.text = """import org.gradle.logging.internal.*
                           File outputFile = file("${loggingOutputFile.toURI()}")
                           OutputEventListener outputEventListener = new OutputEventListener() {
                                void onOutput(OutputEvent event) {
                                    if (event instanceof ProgressStartEvent) {
                                        outputFile << "[START \$event.description]\\n"
                                    } else if (event instanceof ProgressEvent) {
                                        outputFile << "[\$event.status]\\n"
                                    } else if (event instanceof ProgressCompleteEvent) {
                                        outputFile << "[END \$event.description]\\n"
                                    }
                                }
                           }
                           def loggingOutputInternal = gradle.services.get(LoggingOutputInternal)
                           loggingOutputInternal.addOutputEventListener(outputEventListener)
                           buildFinished{
                                loggingOutputInternal.removeOutputEventListener(outputEventListener)
                           }"""
        executer.usingInitScript(initFile)

        return new Statement() {


            @Override
            public void evaluate() throws Throwable {
                try {
                    base.evaluate();
                } finally {
                    resetExpectations()
                    if (initFile != null) {
                        initFile.delete()
                    }
                }
            }
        };
    }
}
