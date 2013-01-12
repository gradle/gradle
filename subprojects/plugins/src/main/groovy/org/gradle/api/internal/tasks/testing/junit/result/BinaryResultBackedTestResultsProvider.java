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

package org.gradle.api.internal.tasks.testing.junit.result;

import org.gradle.api.tasks.testing.TestOutputEvent;

import java.io.File;
import java.io.Writer;
import java.util.HashMap;
import java.util.Map;

public class BinaryResultBackedTestResultsProvider implements TestResultsProvider {
    private final Iterable<File> binaryResultDirs;
    private Map<String, TestClassResult> results;

    public BinaryResultBackedTestResultsProvider(Iterable<File> binaryResultDirs) {
        this.binaryResultDirs = binaryResultDirs;
    }

    public Map<String, TestClassResult> getResults() {
        if (results == null) {
            TestResultSerializer serializer = new TestResultSerializer();
            results = new HashMap<String, TestClassResult>();
            for (File dir : binaryResultDirs) {
                results.putAll(serializer.read(dir));
            }
        }
        return results;
    }

    public void writeOutputs(String className, TestOutputEvent.Destination destination, Writer writer) {
    }
}
