/*
 * Copyright 2015 the original author or authors.
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

import org.gradle.api.internal.file.TestFiles;
import org.gradle.model.internal.core.MutableModelNode;
import org.gradle.model.internal.core.rule.describe.SimpleModelRuleDescriptor;
import org.gradle.model.internal.type.ModelType;
import org.gradle.nativeplatform.BuildType;
import org.gradle.nativeplatform.Flavor;
import org.gradle.nativeplatform.NativeBinarySpec;
import org.gradle.nativeplatform.internal.configure.NativeBinaries;
import org.gradle.nativeplatform.internal.resolve.NativeDependencyResolver;
import org.gradle.nativeplatform.platform.NativePlatform;
import org.gradle.platform.base.SourceComponentSpec;
import org.gradle.platform.base.binary.BaseBinaryFixtures;
import org.gradle.platform.base.internal.BinaryNamingScheme;

public class TestNativeBinariesFactory {

    public static <T extends NativeBinarySpec, I extends AbstractNativeBinarySpec> T create(Class<T> publicType, Class<I> implType, String name, MutableModelNode componentNode,
                                                                                      BinaryNamingScheme namingScheme, NativeDependencyResolver resolver,
                                                                                      NativePlatform platform, BuildType buildType, Flavor flavor) {
        T binary = BaseBinaryFixtures.create(publicType, implType, name, componentNode);
        NativeBinaries.initialize(binary, namingScheme, resolver, TestFiles.fileCollectionFactory(), platform, buildType, flavor);
        SourceComponentSpec component = componentNode.asImmutable(ModelType.of(SourceComponentSpec.class), new SimpleModelRuleDescriptor("get component of " + name)).getInstance();
        binary.getInputs().addAll(component.getSources().values());
        return binary;
    }
}
