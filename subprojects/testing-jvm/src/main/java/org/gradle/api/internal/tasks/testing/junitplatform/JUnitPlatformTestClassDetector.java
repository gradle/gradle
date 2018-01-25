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

import org.gradle.api.internal.tasks.testing.detection.TestFrameworkDetector;
import org.gradle.api.internal.tasks.testing.junit.JUnitTestClassDetector;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

public class JUnitPlatformTestClassDetector extends JUnitTestClassDetector {
    JUnitPlatformTestClassDetector(final TestFrameworkDetector detector) {
        super(detector);
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
        if (!isTest()) {
            return new MethodVisitor(Opcodes.ASM6) {
                @Override
                public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
                    if (desc.startsWith("Lorg/junit/jupiter/api")) {
                        setTest(true);
                    }
                    return null;
                }
            };
        } else {
            return null;
        }
    }
}
