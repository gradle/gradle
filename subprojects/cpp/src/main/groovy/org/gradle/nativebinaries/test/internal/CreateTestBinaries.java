/*
 * Copyright 2013 the original author or authors.
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
package org.gradle.nativebinaries.test.internal;

import org.gradle.api.Action;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.language.base.BinaryContainer;
import org.gradle.language.base.internal.BinaryNamingSchemeBuilder;
import org.gradle.language.base.internal.DefaultBinaryNamingSchemeBuilder;
import org.gradle.model.ModelRule;
import org.gradle.nativebinaries.BuildTypeContainer;
import org.gradle.nativebinaries.FlavorContainer;
import org.gradle.nativebinaries.ProjectNativeComponent;
import org.gradle.nativebinaries.test.TestSuite;
import org.gradle.nativebinaries.test.TestSuiteContainer;
import org.gradle.nativebinaries.internal.configure.NativeBinariesFactory;
import org.gradle.nativebinaries.internal.configure.ProjectNativeComponentInitializer;
import org.gradle.nativebinaries.internal.resolve.NativeDependencyResolver;
import org.gradle.nativebinaries.platform.PlatformContainer;
import org.gradle.nativebinaries.toolchain.internal.ToolChainRegistryInternal;

import java.io.File;

public class CreateTestBinaries extends ModelRule {
    private final Instantiator instantiator;
    private final NativeDependencyResolver resolver;
    private final File binaryOutputDir;

    public CreateTestBinaries(Instantiator instantiator, NativeDependencyResolver resolver, File buildDir) {
        this.instantiator = instantiator;
        this.resolver = resolver;
        this.binaryOutputDir = new File(buildDir, "binaries");
    }

    public void create(BinaryContainer binaries, TestSuiteContainer testSuites, ToolChainRegistryInternal toolChains,
                       PlatformContainer platforms, BuildTypeContainer buildTypes, FlavorContainer flavors) {

        NativeBinariesFactory factory = new TestBinariesFactory(instantiator, resolver, binaryOutputDir);
        BinaryNamingSchemeBuilder namingSchemeBuilder = new DefaultBinaryNamingSchemeBuilder();
        Action<ProjectNativeComponent> createBinariesAction =
                new ProjectNativeComponentInitializer(factory, namingSchemeBuilder, toolChains, platforms, buildTypes, flavors);

        for (TestSuite testSuite : testSuites) {
            createBinariesAction.execute(testSuite);
            binaries.addAll(testSuite.getBinaries());
        }
    }
}
