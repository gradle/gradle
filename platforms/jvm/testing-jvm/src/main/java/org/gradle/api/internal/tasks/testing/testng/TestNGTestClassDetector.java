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
import org.gradle.model.internal.asm.AsmConstants;
import org.gradle.api.internal.tasks.testing.detection.TestClassVisitor;
import org.gradle.api.internal.tasks.testing.detection.TestFrameworkDetector;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.MethodVisitor;

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

    TestNGTestClassDetector(final TestFrameworkDetector detector) {
        super(detector);
    }

    @Override
    protected boolean ignoreNonStaticInnerClass() {
        return false;
    }

    @Override
    public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
        if ("Lorg/testng/annotations/Test;".equals(desc)) {
            setTest(true);
        }
        return null;
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
        if (!isAbstract() && !isTest()) {
            return new TestNGTestMethodDetector();
        } else {
            return null;
        }
    }

    private class TestNGTestMethodDetector extends MethodVisitor {
        private TestNGTestMethodDetector() {
            super(AsmConstants.ASM_LEVEL);

        }

        @Override
        public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
            if (TEST_METHOD_ANNOTATIONS.contains(desc)) {
                setTest(true);
            }
            return null;
        }
    }
}
