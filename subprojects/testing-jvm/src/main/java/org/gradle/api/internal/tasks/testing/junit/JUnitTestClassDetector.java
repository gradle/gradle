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
package org.gradle.api.internal.tasks.testing.junit;

import org.gradle.internal.classanalysis.AsmConstants;
import org.gradle.api.internal.tasks.testing.detection.TestClassVisitor;
import org.gradle.api.internal.tasks.testing.detection.TestFrameworkDetector;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.MethodVisitor;

class JUnitTestClassDetector extends TestClassVisitor {
    JUnitTestClassDetector(final TestFrameworkDetector detector) {
        super(detector);
    }

    @Override
    protected boolean ignoreNonStaticInnerClass(){
        return true;
    }

    @Override
    public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
        if ("Lorg/junit/runner/RunWith;".equals(desc)) {
            setTest(true);
        }

        return null;
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
        if (!isTest()) {
            return new MethodVisitor(AsmConstants.ASM_LEVEL) {
                @Override
                public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
                    if ("Lorg/junit/Test;".equals(desc)) {
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
