/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.nativeplatform.test.internal;

import com.google.common.collect.Lists;
import org.gradle.api.Project;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.project.ProjectRegistry;
import org.gradle.api.internal.resolve.ProjectModelResolver;
import org.gradle.model.ModelMap;
import org.gradle.model.internal.registry.ModelRegistry;
import org.gradle.model.internal.type.ModelTypes;
import org.gradle.nativeplatform.internal.NativeBinarySpecInternal;
import org.gradle.nativeplatform.internal.NativeDependentBinariesResolutionStrategy;
import org.gradle.nativeplatform.test.NativeTestSuiteBinarySpec;
import org.gradle.nativeplatform.test.NativeTestSuiteSpec;
import org.gradle.platform.base.VariantComponentSpec;
import org.gradle.platform.base.internal.BinarySpecInternal;
import org.gradle.testing.base.plugins.TestingModelBasePlugin;

import java.util.Collections;
import java.util.List;

public class NativeDependentTestSuiteBinariesResolutionStrategy extends NativeDependentBinariesResolutionStrategy {

    public NativeDependentTestSuiteBinariesResolutionStrategy(ProjectRegistry<ProjectInternal> projectRegistry, ProjectModelResolver projectModelResolver) {
        super(projectRegistry, projectModelResolver);
    }

    @Override
    protected boolean isTestSuite(BinarySpecInternal target) {
        return target instanceof NativeTestSuiteBinarySpec;
    }

    @Override
    protected List<NativeBinarySpecInternal> getExtraBinaries(Project project, ProjectModelResolver projectModelResolver) {
        if (project.getPlugins().hasPlugin(TestingModelBasePlugin.class)) {
            ModelRegistry modelRegistry = projectModelResolver.resolveProjectModel(project.getPath());
            ModelMap<NativeTestSuiteSpec> testSuites = modelRegistry.realize("testSuites", ModelTypes.modelMap(NativeTestSuiteSpec.class));
            return allBinariesOf(testSuites.withType(VariantComponentSpec.class));
        }
        return Collections.emptyList();
    }

    @Override
    protected List<NativeBinarySpecInternal> getExtraDependents(NativeBinarySpecInternal nativeBinary) {
        if (nativeBinary instanceof NativeTestSuiteBinarySpec) {
            NativeBinarySpecInternal testedBinary = (NativeBinarySpecInternal) ((NativeTestSuiteBinarySpec) nativeBinary).getTestedBinary();
            return Lists.newArrayList(testedBinary);
        }
        return Collections.emptyList();
    }
}
