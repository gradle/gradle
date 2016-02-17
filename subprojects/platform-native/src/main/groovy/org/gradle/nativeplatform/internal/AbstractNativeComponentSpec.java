/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.nativeplatform.internal;

import org.gradle.nativeplatform.NativeComponentSpec;
import org.gradle.nativeplatform.ObjectFile;
import org.gradle.platform.base.TransformationFileType;
import org.gradle.platform.base.component.BaseComponentSpec;
import org.gradle.platform.base.internal.HasIntermediateOutputsComponentSpec;
import org.gradle.util.GUtil;

import java.util.Collections;
import java.util.Set;

public abstract class AbstractNativeComponentSpec extends BaseComponentSpec implements NativeComponentSpec, HasIntermediateOutputsComponentSpec {
    private String baseName;

    @Override
    public String getBaseName() {
        return GUtil.elvis(baseName, getName());
    }

    @Override
    public void setBaseName(String baseName) {
        this.baseName = baseName;
    }

    @Override
    public Set<? extends Class<? extends TransformationFileType>> getIntermediateTypes() {
        return Collections.singleton(ObjectFile.class);
    }
}
