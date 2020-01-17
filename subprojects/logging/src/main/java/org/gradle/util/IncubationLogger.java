/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.util;

import org.gradle.internal.featurelifecycle.IncubatingFeatureUsage;
import org.gradle.internal.featurelifecycle.LoggingIncubatingFeatureHandler;

import javax.annotation.concurrent.ThreadSafe;

@ThreadSafe
public class IncubationLogger {
    public static final String INCUBATION_MESSAGE = "%s is an incubating feature.";

    private static LoggingIncubatingFeatureHandler incubatingFeatureHandler = new LoggingIncubatingFeatureHandler();

    public synchronized static void reset() {
        incubatingFeatureHandler.reset();
    }

    public static synchronized void incubatingFeatureUsed(String incubatingFeature) {
        incubatingFeatureHandler.featureUsed(new IncubatingFeatureUsage(incubatingFeature, IncubationLogger.class));
    }

}
