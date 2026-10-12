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

import org.gradle.language.base.internal.registry.LanguageTransform;
import org.gradle.nativeplatform.toolchain.internal.ToolType;

@SuppressWarnings("deprecation")
public abstract class NativeLanguageTransform<U extends org.gradle.language.base.LanguageSourceSet> implements LanguageTransform<U, org.gradle.nativeplatform.ObjectFile> {

    @Override
    public boolean applyToBinary(org.gradle.platform.base.BinarySpec binary) {
        return binary instanceof org.gradle.nativeplatform.NativeBinarySpec;
    }

    public abstract ToolType getToolType();

    @Override
    public Class<org.gradle.nativeplatform.ObjectFile> getOutputType() {
        return org.gradle.nativeplatform.ObjectFile.class;
    }

}
