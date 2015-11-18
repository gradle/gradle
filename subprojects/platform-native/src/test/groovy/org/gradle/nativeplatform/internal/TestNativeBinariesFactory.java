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

import org.gradle.api.internal.project.taskfactory.ITaskFactory;
import org.gradle.nativeplatform.BuildType;
import org.gradle.nativeplatform.Flavor;
import org.gradle.nativeplatform.NativeBinarySpec;
import org.gradle.nativeplatform.internal.configure.NativeBinaries;
import org.gradle.nativeplatform.internal.resolve.NativeDependencyResolver;
import org.gradle.nativeplatform.platform.NativePlatform;
import org.gradle.platform.base.binary.BaseBinaryFixtures;
import org.gradle.platform.base.binary.BaseBinarySpec;
import org.gradle.platform.base.internal.BinaryNamingScheme;
import org.gradle.platform.base.internal.ComponentSpecInternal;

public class TestNativeBinariesFactory {

    public static <T extends BaseBinarySpec> T create(Class<T> type, String name, ITaskFactory taskFactory, final ComponentSpecInternal component,
                                                      final BinaryNamingScheme namingScheme, final NativeDependencyResolver resolver,
                                                      final NativePlatform platform, final BuildType buildType, final Flavor flavor) {
        T binary = BaseBinaryFixtures.create(type, type, name, component, taskFactory);
        NativeBinaries.initialize((NativeBinarySpec) binary, namingScheme, resolver, platform, buildType, flavor);
        binary.getInputs().addAll(component.getSources().values());
        return binary;
    }
}
