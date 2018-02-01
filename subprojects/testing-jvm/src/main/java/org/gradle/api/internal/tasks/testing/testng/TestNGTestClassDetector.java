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
package org.gradle.api.internal.tasks.testing.testng;

import com.google.common.collect.ImmutableSet;
import org.gradle.api.internal.tasks.testing.detection.TestClassVisitor;
import org.gradle.api.internal.tasks.testing.detection.TestFrameworkDetector;

import java.util.Set;

class TestNGTestClassDetector extends TestClassVisitor {
    private static final Set<String> TEST_METHOD_ANNOTATIONS =
        ImmutableSet.<String>builder()
        .add("Lorg/testng/annotations/Test;")
        .add("Lorg/testng/annotations/BeforeSuite;")
        .add("Lorg/testng/annotations/AfterSuite;")
        .add("Lorg/testng/annotations/BeforeTest;")
        .add("Lorg/testng/annotations/AfterTest;")
        .add("Lorg/testng/annotations/BeforeGroups;")
        .add("Lorg/testng/annotations/AfterGroups;")
        .add("Lorg/testng/annotations/Factory;")
        .build();
    private static final Set<String> TEST_CLASS_ANNOTATIONS=
        ImmutableSet.of("Lorg/testng/annotations/Test;");

    TestNGTestClassDetector(final TestFrameworkDetector detector) {
        super(detector);
    }

    @Override
    protected boolean ignoreMethodsInAbstractClass() {
        return false;
    }

    @Override
    protected boolean ignoreNonStaticInnerClass() {
        return false;
    }

    @Override
    protected Set<String> getTestMethodAnnotations() {
        return TEST_METHOD_ANNOTATIONS;
    }

    @Override
    protected Set<String> getTestClassAnnotations() {
        return TEST_CLASS_ANNOTATIONS;
    }
}
