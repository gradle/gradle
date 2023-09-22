/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.internal.featurelifecycle;

public class SimulatedSingleMessageLogger {

    public static final String DIRECT_CALL = "direct call";
    public static final String INDIRECT_CALL = "indirect call";
    public static final String INDIRECT_CALL_2 = "second-level indirect call";

    public static TestFeatureUsage indirectlySecondLevel(String message) {
        return indirectly(message);
    }

    public static TestFeatureUsage indirectly(String message) {
        return nagUserWith(message);
    }

    public static TestFeatureUsage nagUserWith(String message) {
        return new TestFeatureUsage(message, SimulatedSingleMessageLogger.class);
    }

    static class TestFeatureUsage extends FeatureUsage {
        public final Exception exception = new Exception();

        public TestFeatureUsage(String summary, Class<?> calledFrom) {
            super(summary, calledFrom);
        }
    }
}
