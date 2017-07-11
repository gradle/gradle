/*
 * Copyright 2009 the original author or authors.
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

package org.gradle.api.internal.tasks.testing.detection;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Opcodes;

/**
 * Base class for ASM test class scanners.
 */
public abstract class TestClassVisitor extends ClassVisitor {

    protected final TestFrameworkDetector detector;

    protected TestClassVisitor(TestFrameworkDetector detector) {
        super(Opcodes.ASM6);
        if (detector == null) {
            throw new IllegalArgumentException("detector == null!");
        }
        this.detector = detector;
    }

    public abstract String getClassName();

    public abstract boolean isTest();

    public abstract boolean isAbstract();

    public abstract String getSuperClassName();
}
