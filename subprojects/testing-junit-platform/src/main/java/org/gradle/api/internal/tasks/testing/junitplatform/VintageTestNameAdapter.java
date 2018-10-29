/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.api.internal.tasks.testing.junitplatform;

import org.gradle.api.internal.tasks.testing.junit.JUnitTestEventAdapter;
import org.junit.platform.engine.TestDescriptor;
import org.junit.platform.engine.TestSource;
import org.junit.platform.engine.UniqueId;
import org.junit.platform.engine.support.descriptor.ClassSource;
import org.junit.platform.launcher.TestIdentifier;

import java.util.List;

class VintageTestNameAdapter {
    private static final String VINTAGE_DESCRIPTOR_CLASS_NAME = "VintageTestDescriptor";
    private static final String VINTAGE_ENGINE = "[engine:junit-vintage]";

    static boolean isVintageDynamicLeafTest(TestIdentifier test) {
        return test.getParentId().isPresent()
            && !VINTAGE_ENGINE.equals(test.getParentId().get())
            && isClassAndTest(test);
    }

    private static boolean isClassAndTest(TestIdentifier test) {
        return test.getSource().isPresent()
            && test.getSource().get() instanceof ClassSource
            && test.isTest();
    }

    static boolean isVintageDynamicLeafTest(TestDescriptor test, TestSource source) {
        return test.isTest()
            && source instanceof ClassSource
            && VINTAGE_DESCRIPTOR_CLASS_NAME.equals(test.getClass().getSimpleName());
    }

    static String vintageDynamicClassName(UniqueId uniqueId) {
        return JUnitTestEventAdapter.className(vintageTestName(uniqueId));
    }

    static String vintageDynamicMethodName(UniqueId uniqueId) {
        return JUnitTestEventAdapter.methodName(vintageTestName(uniqueId));
    }

    private static String vintageTestName(UniqueId uniqueId) {
        List<UniqueId.Segment> segments = uniqueId.getSegments();
        return segments.get(segments.size() - 1).getValue();
    }
}
