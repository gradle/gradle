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
import org.gradle.util.TemporaryFolder
import org.gradle.util.TestFile
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

class ProgressLoggingFixture implements TestRule {

    private TemporaryFolder temporaryFolder = new TemporaryFolder();

    private TestFile loggingOutputFile = null
    private TestFile initFile = null
    GradleExecuter executer = null

    void withProgressLogging(GradleExecuter executer, TestFile... testFiles) {
        writeSomeContent(testFiles)
        this.executer = executer
        initFile = temporaryFolder.file("init.gradle")
        loggingOutputFile = temporaryFolder.file("loggingoutput.log")

        initFile.text = """import org.gradle.logging.internal.*
gradle.services.get(LoggingOutputInternal).addOutputEventListener(new OutputEventListener() {
    File outputFile  = new File("${loggingOutputFile.absolutePath.replace("\\", "/")}")
    void onOutput(OutputEvent event) {
        if (event instanceof ProgressStartEvent) {
            outputFile << "[START \$event.description]\\n"
        } else if (event instanceof ProgressEvent) {
            outputFile << "[\$event.status]\\n"
        } else if (event instanceof ProgressCompleteEvent) {
            outputFile << "[END]\\n"
        }
    }
})
        """
        executer.usingInitScript(initFile)
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
        assert loggingOutputFile != null
        assert loggingOutputFile != null

        def lines = loggingOutputFile.readLines()
        def startIndex = lines.indexOf("[START " + operation + " " + url + "]")
        if (startIndex == -1) {
            return false
        }
        lines = lines[startIndex..<lines.size()]
        lines = lines[0..lines.indexOf("[END]")]
        lines.size() > 2
    }

    void resetExpectations() {
        initFile = null;
        loggingOutputFile = null
    }

    boolean noProgressLogged() {
        loggingOutputFile == null || loggingOutputFile.text.isEmpty()
    }

    Statement apply(Statement base, Description description) {
        return new Statement() {
            @Override
            public void evaluate() throws Throwable {
                try {
                    base.evaluate();
                } finally {
                    resetExpectations()
                }
            }

        };
    }
}
