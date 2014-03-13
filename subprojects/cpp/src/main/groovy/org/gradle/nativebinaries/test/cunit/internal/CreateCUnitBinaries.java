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
package org.gradle.nativebinaries.test.cunit.internal;

import org.gradle.api.Action;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.plugins.ExtensionAware;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.language.base.BinaryContainer;
import org.gradle.language.base.LanguageSourceSet;
import org.gradle.language.base.internal.BinaryNamingScheme;
import org.gradle.language.base.internal.DefaultBinaryNamingSchemeBuilder;
import org.gradle.nativebinaries.ProjectNativeBinary;
import org.gradle.nativebinaries.internal.ProjectNativeBinaryInternal;
import org.gradle.nativebinaries.internal.resolve.NativeDependencyResolver;
import org.gradle.nativebinaries.language.internal.DefaultPreprocessingTool;
import org.gradle.nativebinaries.test.TestSuiteExecutableBinary;
import org.gradle.nativebinaries.test.cunit.CUnitTestSuite;
import org.gradle.nativebinaries.test.internal.DefaultTestSuiteExecutableBinary;
import org.gradle.nativebinaries.toolchain.internal.ToolChainInternal;

import java.io.File;

public class CreateCUnitBinaries {
    private final ProjectInternal project;
    private final Instantiator instantiator;
    private final NativeDependencyResolver resolver;

    public CreateCUnitBinaries(ProjectInternal project, Instantiator instantiator, NativeDependencyResolver resolver) {
        this.project = project;
        this.instantiator = instantiator;
        this.resolver = resolver;
    }

    public void apply(final CUnitTestSuite cUnitTestSuite, final BinaryContainer binaries) {
        cUnitTestSuite.getTestedComponent().getBinaries().withType(ProjectNativeBinaryInternal.class).all(new Action<ProjectNativeBinaryInternal>() {
            public void execute(ProjectNativeBinaryInternal testedBinary) {
                final ProjectNativeBinary cunitExe = createTestBinary(cUnitTestSuite, testedBinary, project);
                ((ExtensionAware) cunitExe).getExtensions().create("cCompiler", DefaultPreprocessingTool.class);

                cUnitTestSuite.getBinaries().add(cunitExe);
                binaries.add(cunitExe);

                testedBinary.getSource().all(new Action<LanguageSourceSet>() {
                    public void execute(LanguageSourceSet languageSourceSet) {
                        cunitExe.source(languageSourceSet);
                    }
                });
            }
        });
    }

    public ProjectNativeBinary createTestBinary(CUnitTestSuite cUnitTestSuite, ProjectNativeBinaryInternal testedBinary, ProjectInternal project) {
        BinaryNamingScheme namingScheme = new DefaultBinaryNamingSchemeBuilder(testedBinary.getNamingScheme())
                .withComponentName(cUnitTestSuite.getBaseName())
                .withTypeString("CUnitExe").build();

        ProjectNativeBinary testBinary = instantiator.newInstance(DefaultTestSuiteExecutableBinary.class,
                cUnitTestSuite, testedBinary.getFlavor(), testedBinary.getToolChain(),
                testedBinary.getTargetPlatform(), testedBinary.getBuildType(), namingScheme, resolver);

        setupDefaults(testBinary, project);
        return testBinary;
    }

    private void setupDefaults(ProjectNativeBinary nativeBinary, ProjectInternal project) {
        BinaryNamingScheme namingScheme = ((ProjectNativeBinaryInternal) nativeBinary).getNamingScheme();
        File binaryOutputDir = new File(new File(project.getBuildDir(), "binaries"), namingScheme.getOutputDirectoryBase());
        String baseName = nativeBinary.getComponent().getBaseName();

        ToolChainInternal tc = (ToolChainInternal) nativeBinary.getToolChain();
        ((TestSuiteExecutableBinary) nativeBinary).setExecutableFile(new File(binaryOutputDir, tc.getExecutableName(baseName)));
    }

}
