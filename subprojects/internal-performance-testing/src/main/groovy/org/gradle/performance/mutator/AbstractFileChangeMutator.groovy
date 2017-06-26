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

package org.gradle.performance.mutator

import org.gradle.performance.fixture.BuildExperimentInvocationInfo
import org.gradle.performance.fixture.BuildExperimentListener
import org.gradle.performance.fixture.BuildExperimentListenerAdapter
import org.gradle.performance.measure.MeasuredOperation

abstract class AbstractFileChangeMutator extends BuildExperimentListenerAdapter {
    protected final String sourceFilePath
    private String originalText
    private long timestamp
    protected int counter

    protected AbstractFileChangeMutator(String sourceFilePath) {
        this.sourceFilePath = sourceFilePath
        this.timestamp = System.currentTimeMillis()
    }

    void setTimestamp(long timestamp) {
        this.timestamp = timestamp
    }

    /**
     * Returns some text that is unlikely to have been included in any previous version of the target source file.
     * The string can be used as a Java identifier.
     */
    protected String getUniqueText() {
        return "_" + String.valueOf(timestamp) + "_" + counter
    }

    @Override
    void beforeInvocation(BuildExperimentInvocationInfo invocationInfo) {
        change(invocationInfo.projectDir)
    }

    @Override
    void afterInvocation(BuildExperimentInvocationInfo invocationInfo, MeasuredOperation operation, BuildExperimentListener.MeasurementCallback measurementCallback) {
        revert(invocationInfo.projectDir)
    }

    private change(File projectDir) {
        File sourceFile = new File(projectDir, sourceFilePath)
        if (originalText == null) {
            originalText = sourceFile.text
        }
        counter++
        StringBuilder modifiedText = new StringBuilder(originalText)
        applyChangeTo(modifiedText)

        println "Changing " + sourceFile
        sourceFile.text = modifiedText
    }

    private void revert(File projectDir) throws IOException {
        def sourceFile = new File(projectDir, sourceFilePath)

        println "Reverting to original " + sourceFile
        sourceFile.text = originalText
    }

    protected abstract void applyChangeTo(StringBuilder text)

    String toString() {
        getClass().simpleName + '(' + sourceFile + ')';
    }
}
