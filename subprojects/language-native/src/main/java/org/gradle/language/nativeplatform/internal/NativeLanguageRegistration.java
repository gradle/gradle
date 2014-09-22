/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.language.nativeplatform.internal;

import org.gradle.nativeplatform.ObjectFile;
import org.gradle.platform.base.TransformationFileType;
import org.gradle.language.base.LanguageSourceSet;
import org.gradle.language.base.internal.LanguageRegistration;
import org.gradle.nativeplatform.NativeBinarySpec;
import org.gradle.platform.base.BinarySpec;

public abstract class NativeLanguageRegistration<U extends LanguageSourceSet> implements LanguageRegistration<U> {
    public boolean applyToBinary(BinarySpec binary) {
        return binary instanceof NativeBinarySpec;
    }
    public Class<? extends TransformationFileType> getOutputType() {
        return ObjectFile.class;
    }
}
