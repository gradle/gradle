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
import org.gradle.jvm.JvmApiSpec;
import org.gradle.jvm.JvmByteCode;
import org.gradle.jvm.JvmResources;
import org.gradle.platform.base.DependencySpecContainer;
import org.gradle.platform.base.TransformationFileType;
import org.gradle.platform.base.component.BaseComponentSpec;
import org.gradle.platform.base.internal.DefaultDependencySpecContainer;
import org.gradle.platform.base.internal.DefaultPlatformRequirement;
import org.gradle.platform.base.internal.PlatformRequirement;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class DefaultJvmLibrarySpec extends BaseComponentSpec implements JvmLibrarySpecInternal {

    public static Set<Class<? extends TransformationFileType>> defaultJvmComponentInputTypes() {
        final Set<Class<? extends TransformationFileType>> inputTypes = new HashSet<Class<? extends TransformationFileType>>();
        inputTypes.add(JvmResources.class);
        inputTypes.add(JvmByteCode.class);
        return inputTypes;
    }

    private final List<PlatformRequirement> targetPlatforms = Lists.newArrayList();
    private final JvmApiSpec apiSpec = new DefaultJvmApiSpec();
    private final DependencySpecContainer dependencies = new DefaultDependencySpecContainer();

    @Override
    protected String getTypeName() {
        return "JVM library";
    }

    @Override
    public Set<Class<? extends TransformationFileType>> getIntermediateTypes() {
        return defaultJvmComponentInputTypes();
    }

    @Override
    public List<PlatformRequirement> getTargetPlatforms() {
        return Collections.unmodifiableList(targetPlatforms);
    }

    @Override
    public void targetPlatform(String targetPlatform) {
        this.targetPlatforms.add(DefaultPlatformRequirement.create(targetPlatform));
    }

    @Override
    public JvmApiSpec getApi() {
        return apiSpec;
    }

    @Override
    public DependencySpecContainer getDependencies() {
        return dependencies;
    }
}
