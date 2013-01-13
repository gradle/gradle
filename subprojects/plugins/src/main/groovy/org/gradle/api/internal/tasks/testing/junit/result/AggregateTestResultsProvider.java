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

import org.gradle.api.Action;
import org.gradle.api.tasks.testing.TestOutputEvent;

import java.io.File;
import java.io.Writer;
import java.util.HashMap;
import java.util.Map;

public class AggregateTestResultsProvider implements TestResultsProvider {
    private final Iterable<File> binaryResultDirs;
    private Map<String, TestResultsProvider> classOutputProviders;

    public AggregateTestResultsProvider(Iterable<File> binaryResultDirs) {
        this.binaryResultDirs = binaryResultDirs;
    }

    public void visitClasses(final Action<? super TestClassResult> visitor) {
        classOutputProviders = new HashMap<String, TestResultsProvider>();
        for (File dir : binaryResultDirs) {
            final BinaryResultBackedTestResultsProvider provider = new BinaryResultBackedTestResultsProvider(dir);
            provider.visitClasses(new Action<TestClassResult>() {
                public void execute(TestClassResult classResult) {
                    if (!classOutputProviders.containsKey(classResult.getClassName())) {
                        classOutputProviders.put(classResult.getClassName(), provider);
                        visitor.execute(classResult);
                    }
                }
            });
        }
    }

    public void writeOutputs(String className, TestOutputEvent.Destination destination, Writer writer) {
        classOutputProviders.get(className).writeOutputs(className, destination, writer);
    }
}
