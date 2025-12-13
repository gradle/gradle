/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.docs.samples;

import org.junit.platform.engine.UniqueId;
import org.junit.platform.engine.support.descriptor.AbstractTestDescriptor;
import org.junit.platform.engine.support.descriptor.ClassSource;

import static org.gradle.docs.samples.SamplesTestEngine.SAMPLES_TEST_ENGINE_UID;

public class BucketClassTestDescriptor extends AbstractTestDescriptor {
    public static final String BUCKET_CLASS_SEGMENT = "class";

    public static UniqueId getClassUid(String className) {
        return SAMPLES_TEST_ENGINE_UID.append(BUCKET_CLASS_SEGMENT, className);
    }

    protected BucketClassTestDescriptor(Class<?> testClass) {
        super(getClassUid(testClass.getName()), testClass.getName(), ClassSource.from(testClass));
    }

    @Override
    public Type getType() {
        return Type.CONTAINER;
    }

    public String getClassName() {
        return ((ClassSource) getSource().get()).getClassName();
    }
}
