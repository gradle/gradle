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

package org.gradle.jvm.internal;

import com.google.common.collect.Lists;
import org.gradle.jvm.JvmByteCode;
import org.gradle.jvm.JvmLibrarySpec;
import org.gradle.jvm.JvmResources;
import org.gradle.platform.base.TransformationFileType;
import org.gradle.platform.base.component.BaseComponentSpec;
import org.gradle.platform.base.internal.ComponentSpecInternal;

import java.util.*;

public class DefaultJvmLibrarySpec extends BaseComponentSpec implements JvmLibrarySpec, ComponentSpecInternal {
    private final Set<Class<? extends TransformationFileType>> languageOutputs = new HashSet<Class<? extends TransformationFileType>>();
    private final List<String> targets = new ArrayList<String>();

    public DefaultJvmLibrarySpec() {
        this.languageOutputs.add(JvmResources.class);
        this.languageOutputs.add(JvmByteCode.class);
    }

    public Set<Class<? extends TransformationFileType>> getInputTypes() {
        return languageOutputs;
    }

    public List<String> getTargetPlatforms() {
        return Lists.newArrayList(targets);
    }

    public void targetPlatform(String... targets) {
        Collections.addAll(this.targets, targets);
    }
}
