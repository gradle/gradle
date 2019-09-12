/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.api.internal.tasks.testing;

import org.gradle.api.tasks.testing.TestDescriptor;
import org.gradle.api.tasks.testing.TestOutputEvent;
import org.gradle.api.tasks.testing.TestOutputListener;

import java.util.HashMap;
import java.util.Map;

public class TestOutputCollectingListener implements TestOutputListener {

    Map<TestDescriptor, String> outputs = new HashMap<TestDescriptor, String>();
    Map<TestDescriptor, String> errors = new HashMap<TestDescriptor, String>();

    @Override
    public void onOutput(TestDescriptor testDescriptor, TestOutputEvent outputEvent) {
        if (outputEvent.getDestination() == TestOutputEvent.Destination.StdOut) {
            String prev = outputs.get(testDescriptor);
            if (prev == null) {
                prev = "";
            }
            outputs.put(testDescriptor, prev + outputEvent.getMessage());
        } else if (outputEvent.getDestination() == TestOutputEvent.Destination.StdErr) {

            String prev = errors.get(testDescriptor);
            if (prev == null) {
                prev = "";
            }
            errors.put(testDescriptor, prev + outputEvent.getMessage());
        }
    }

    public String outputFor(TestDescriptor descriptor) {
        String output = outputs.get(descriptor);
        return output != null ? output : "";
    }

    public String errorFor(TestDescriptor descriptor) {
        String error = errors.get(descriptor);
        return error != null ? error : "";
    }
}
