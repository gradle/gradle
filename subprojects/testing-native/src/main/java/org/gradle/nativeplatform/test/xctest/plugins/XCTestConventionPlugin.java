/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.nativeplatform.test.xctest.plugins;

import com.google.common.collect.Lists;
import org.apache.commons.lang.StringUtils;
import org.gradle.api.Action;
import org.gradle.api.Incubating;
import org.gradle.api.Named;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.attributes.Usage;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.RegularFile;
import org.gradle.api.internal.attributes.ImmutableAttributesFactory;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.language.base.plugins.LifecycleBasePlugin;
import org.gradle.language.cpp.internal.DefaultUsageContext;
import org.gradle.language.cpp.internal.NativeVariantIdentity;
import org.gradle.language.internal.NativeComponentFactory;
import org.gradle.language.nativeplatform.internal.BuildType;
import org.gradle.language.nativeplatform.internal.Names;
import org.gradle.language.nativeplatform.internal.toolchains.ToolChainSelector;
import org.gradle.language.swift.ProductionSwiftComponent;
import org.gradle.language.swift.SwiftApplication;
import org.gradle.language.swift.SwiftBinary;
import org.gradle.language.swift.SwiftComponent;
import org.gradle.language.swift.SwiftPlatform;
import org.gradle.language.swift.internal.DefaultSwiftBinary;
import org.gradle.language.swift.plugins.SwiftBasePlugin;
import org.gradle.language.swift.tasks.SwiftCompile;
import org.gradle.language.swift.tasks.UnexportMainSymbol;
import org.gradle.model.internal.registry.ModelRegistry;
import org.gradle.nativeplatform.OperatingSystemFamily;
import org.gradle.nativeplatform.platform.internal.DefaultNativePlatform;
import org.gradle.nativeplatform.tasks.LinkMachOBundle;
import org.gradle.nativeplatform.test.plugins.NativeTestingBasePlugin;
import org.gradle.nativeplatform.test.xctest.SwiftXCTestBinary;
import org.gradle.nativeplatform.test.xctest.SwiftXCTestBundle;
import org.gradle.nativeplatform.test.xctest.SwiftXCTestSuite;
import org.gradle.nativeplatform.test.xctest.internal.DefaultSwiftXCTestBinary;
import org.gradle.nativeplatform.test.xctest.internal.DefaultSwiftXCTestBundle;
import org.gradle.nativeplatform.test.xctest.internal.DefaultSwiftXCTestExecutable;
import org.gradle.nativeplatform.test.xctest.internal.DefaultSwiftXCTestSuite;
import org.gradle.nativeplatform.test.xctest.tasks.InstallXCTestBundle;
import org.gradle.nativeplatform.test.xctest.tasks.XCTest;
import org.gradle.nativeplatform.toolchain.NativeToolChain;
import org.gradle.nativeplatform.toolchain.internal.NativeToolChainInternal;
import org.gradle.nativeplatform.toolchain.internal.NativeToolChainRegistryInternal;
import org.gradle.nativeplatform.toolchain.internal.PlatformToolProvider;
import org.gradle.nativeplatform.toolchain.internal.xcode.MacOSSdkPlatformPathLocator;
import org.gradle.util.GUtil;

import javax.inject.Inject;
import java.io.File;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;

import static org.gradle.language.cpp.CppBinary.DEBUGGABLE_ATTRIBUTE;
import static org.gradle.language.cpp.CppBinary.OPTIMIZED_ATTRIBUTE;

/**
 * A plugin that sets up the infrastructure for testing native binaries with XCTest test framework. It also adds conventions on top of it.
 *
 * @since 4.2
 */
@Incubating
public class XCTestConventionPlugin implements Plugin<ProjectInternal> {
    private final MacOSSdkPlatformPathLocator sdkPlatformPathLocator;
    private final ToolChainSelector toolChainSelector;
    private final NativeComponentFactory componentFactory;
    private final ObjectFactory objectFactory;
    private final ImmutableAttributesFactory attributesFactory;

    @Inject
    public XCTestConventionPlugin(MacOSSdkPlatformPathLocator sdkPlatformPathLocator, ToolChainSelector toolChainSelector, NativeComponentFactory componentFactory, ObjectFactory objectFactory, ImmutableAttributesFactory attributesFactory) {
        this.sdkPlatformPathLocator = sdkPlatformPathLocator;
        this.toolChainSelector = toolChainSelector;
        this.componentFactory = componentFactory;
        this.objectFactory = objectFactory;
        this.attributesFactory = attributesFactory;
    }

    @Override
    public void apply(ProjectInternal project) {
        project.getPluginManager().apply(SwiftBasePlugin.class);
        project.getPluginManager().apply(NativeTestingBasePlugin.class);

        // Create test suite component
        final DefaultSwiftXCTestSuite testComponent = createTestSuite(project);

        project.afterEvaluate(new Action<Project>() {
            @Override
            public void execute(final Project project) {
                testComponent.getOperatingSystems().lockNow();
                Set<OperatingSystemFamily> operatingSystemFamilies = testComponent.getOperatingSystems().get();
                if (operatingSystemFamilies.isEmpty()) {
                    throw new IllegalArgumentException("An operating system needs to be specified for the application.");
                }

                Usage runtimeUsage = objectFactory.named(Usage.class, Usage.NATIVE_RUNTIME);
                BuildType buildType = BuildType.DEBUG;
                for (OperatingSystemFamily operatingSystem : operatingSystemFamilies) {
                    String operatingSystemSuffix = createDimensionSuffix(operatingSystem, operatingSystemFamilies);
                    String variantName = buildType.getName() + operatingSystemSuffix;

                    Provider<String> group = project.provider(new Callable<String>() {
                        @Override
                        public String call() throws Exception {
                            return project.getGroup().toString();
                        }
                    });

                    Provider<String> version = project.provider(new Callable<String>() {
                        @Override
                        public String call() throws Exception {
                            return project.getVersion().toString();
                        }
                    });

                    AttributeContainer runtimeAttributes = attributesFactory.mutable();
                    runtimeAttributes.attribute(Usage.USAGE_ATTRIBUTE, runtimeUsage);
                    runtimeAttributes.attribute(DEBUGGABLE_ATTRIBUTE, buildType.isDebuggable());
                    runtimeAttributes.attribute(OPTIMIZED_ATTRIBUTE, buildType.isOptimized());
                    runtimeAttributes.attribute(OperatingSystemFamily.OPERATING_SYSTEM_ATTRIBUTE, operatingSystem);

                    NativeVariantIdentity variantIdentity = new NativeVariantIdentity(variantName, testComponent.getModule(), group, version, buildType.isDebuggable(), buildType.isOptimized(), operatingSystem,
                        null,
                        new DefaultUsageContext(variantName + "-runtime", runtimeUsage, runtimeAttributes));

                    if (DefaultNativePlatform.getCurrentOperatingSystem().toFamilyName().equals(operatingSystem.getName())) {
                        ToolChainSelector.Result<SwiftPlatform> result = toolChainSelector.select(SwiftPlatform.class);

                        // Create test suite executable
                        DefaultSwiftXCTestBinary binary;
                        if (result.getTargetPlatform().getOperatingSystemFamily().isMacOs()) {
                            binary = (DefaultSwiftXCTestBinary) testComponent.addBundle("executable", variantIdentity, result.getTargetPlatform(), result.getToolChain(), result.getPlatformToolProvider());

                        } else {
                            binary = (DefaultSwiftXCTestBinary) testComponent.addExecutable("executable", variantIdentity, result.getTargetPlatform(), result.getToolChain(), result.getPlatformToolProvider());
                        }
                        testComponent.getTestBinary().set(binary);

                        // TODO: Publishing for test executable?
                        final ProductionSwiftComponent mainComponent = project.getComponents().withType(ProductionSwiftComponent.class).findByName("main");
                        if (mainComponent != null) {
                            testComponent.getTestedComponent().set(mainComponent);
                        }
                    }
                }

                testComponent.getBinaries().whenElementKnown(DefaultSwiftXCTestBinary.class, new Action<DefaultSwiftXCTestBinary>() {
                    @Override
                    public void execute(DefaultSwiftXCTestBinary binary) {
                        // Create test suite test task
                        XCTest testingTask = createTestingTask(project);
                        binary.getRunTask().set(testingTask);

                        // Configure tasks
                        configureTestingTask(binary, testingTask);
                        configureTestSuiteBuildingTasks((ProjectInternal) project, binary);

                        configureTestSuiteWithTestedComponentWhenAvailable(project, testComponent, binary);
                    }
                });

                testComponent.getBinaries().realizeNow();
            }
        });
    }

    private String createDimensionSuffix(Named dimensionValue, Collection<? extends Named> multivalueProperty) {
        if (isDimensionVisible(multivalueProperty)) {
            return StringUtils.capitalize(dimensionValue.getName().toLowerCase());
        }
        return "";
    }

    private boolean isDimensionVisible(Collection<? extends Named> multivalueProperty) {
        return multivalueProperty.size() > 1;
    }

    private void configureTestSuiteBuildingTasks(ProjectInternal project, final DefaultSwiftXCTestBinary binary) {
        if (binary instanceof SwiftXCTestBundle) {
            TaskContainer tasks = project.getTasks();
            final Names names = binary.getNames();
            SwiftCompile compile = binary.getCompileTask().get();

            // TODO - creating a bundle should be done by some general purpose plugin

            // TODO - make this lazy
            DefaultNativePlatform currentPlatform = new DefaultNativePlatform("current");
            final ModelRegistry modelRegistry = project.getModelRegistry();
            NativeToolChain toolChain = modelRegistry.realize("toolChains", NativeToolChainRegistryInternal.class).getForPlatform(currentPlatform);

            // Platform specific arguments
            compile.getCompilerArgs().addAll(project.provider(new Callable<List<String>>() {
                @Override
                public List<String> call() {
                    File frameworkDir = new File(sdkPlatformPathLocator.find(), "Developer/Library/Frameworks");
                    return Arrays.asList("-parse-as-library", "-F" + frameworkDir.getAbsolutePath());
                }
            }));

            // Add a link task
            final LinkMachOBundle link = tasks.create(names.getTaskName("link"), LinkMachOBundle.class);
            link.getLinkerArgs().set(project.provider(new Callable<List<String>>() {
                @Override
                public List<String> call() {
                    File frameworkDir = new File(sdkPlatformPathLocator.find(), "Developer/Library/Frameworks");
                    return Lists.newArrayList("-F" + frameworkDir.getAbsolutePath(), "-framework", "XCTest", "-Xlinker", "-rpath", "-Xlinker", "@executable_path/../Frameworks", "-Xlinker", "-rpath", "-Xlinker", "@loader_path/../Frameworks");
                }
            }));

            InstallXCTestBundle install = tasks.create(names.getTaskName("install"), InstallXCTestBundle.class);
            install.getBundleBinaryFile().set(link.getLinkedFile());
            install.getInstallDirectory().set(project.getLayout().getBuildDirectory().dir("install/" + names.getDirName()));
            binary.getInstallDirectory().set(install.getInstallDirectory());

            link.source(binary.getObjects());
            link.lib(binary.getLinkLibraries());
            final PlatformToolProvider toolProvider = ((NativeToolChainInternal) toolChain).select(currentPlatform);
            Provider<RegularFile> exeLocation = project.getLayout().getBuildDirectory().file(project.getProviders().provider(new Callable<String>() {
                @Override
                public String call() {
                    return toolProvider.getExecutableName("exe/" + names.getDirName() + binary.getBaseName().get());
                }
            }));
            link.getLinkedFile().set(exeLocation);
            link.getTargetPlatform().set(currentPlatform);
            link.getToolChain().set(toolChain);
            link.getDebuggable().set(binary.isDebuggable());

            binary.getExecutableFile().set(link.getLinkedFile());

            DefaultSwiftXCTestBundle bundle = (DefaultSwiftXCTestBundle) binary;
            bundle.getLinkTask().set(link);
            bundle.getRunScriptFile().set(install.getRunScriptFile());
        } else {
            DefaultSwiftXCTestExecutable executable = (DefaultSwiftXCTestExecutable) binary;
            executable.getRunScriptFile().set(executable.getInstallTask().get().getRunScriptFile());
        }
    }

    private XCTest createTestingTask(Project project) {
        TaskContainer tasks = project.getTasks();

        XCTest testTask = tasks.create("xcTest", XCTest.class);

        testTask.setGroup(LifecycleBasePlugin.VERIFICATION_GROUP);
        testTask.setDescription("Executes XCTest suites");
        return testTask;
    }

    private void configureTestingTask(SwiftXCTestBinary binary, XCTest testTask) {
        testTask.getTestInstallDirectory().set(binary.getInstallDirectory());
        testTask.getRunScriptFile().set(binary.getRunScriptFile());
        testTask.getWorkingDirectory().set(binary.getInstallDirectory());
    }

    private DefaultSwiftXCTestSuite createTestSuite(final Project project) {
        // TODO - Reuse logic from Swift*Plugin
        // TODO - component name and extension name aren't the same
        // TODO - should use `src/xctest/swift` as the convention?
        // Add the test suite and extension
        DefaultSwiftXCTestSuite testSuite = componentFactory.newInstance(SwiftXCTestSuite.class, DefaultSwiftXCTestSuite.class, "test");

        project.getExtensions().add(SwiftXCTestSuite.class, "xctest", testSuite);
        project.getComponents().add(testSuite);

        // Setup component
        testSuite.getModule().set(GUtil.toCamelCase(project.getName() + "Test"));

        return testSuite;
    }

    private void configureTestSuiteWithTestedComponentWhenAvailable(final Project project, final DefaultSwiftXCTestSuite testSuite, final DefaultSwiftXCTestBinary testExecutable) {
        SwiftComponent target = testSuite.getTestedComponent().getOrNull();
        if (!(target instanceof ProductionSwiftComponent)) {
            return;
        }
        final ProductionSwiftComponent testedComponent = (ProductionSwiftComponent) target;

        final TaskContainer tasks = project.getTasks();
        testedComponent.getBinaries().whenElementFinalized(new Action<SwiftBinary>() {
            @Override
            public void execute(SwiftBinary testedBinary) {
                if (testedBinary != testedComponent.getDevelopmentBinary().get()) {
                    return;
                }

                // If nothing was configured for the test suite source compatibility, use the tested component one.
                if (testSuite.getSourceCompatibility().getOrNull() == null) {
                    testExecutable.getSourceCompatibility().set(testedBinary.getSourceCompatibility());
                }

                // Setup the dependency on the main binary
                // This should all be replaced by a single dependency that points at some "testable" variants of the main binary

                // Inherit implementation dependencies
                testExecutable.getImplementationDependencies().extendsFrom(((DefaultSwiftBinary) testedBinary).getImplementationDependencies());

                // Configure test binary to compile against binary under test
                Dependency compileDependency = project.getDependencies().create(project.files(testedBinary.getModuleFile()));
                testExecutable.getImportPathConfiguration().getDependencies().add(compileDependency);

                // Configure test binary to link against tested component compiled objects
                FileCollection testableObjects;
                if (testedComponent instanceof SwiftApplication) {
                    UnexportMainSymbol unexportMainSymbol = tasks.create("relocateMainForTest", UnexportMainSymbol.class);
                    unexportMainSymbol.getOutputDirectory().set(project.getLayout().getBuildDirectory().dir("obj/main/for-test"));
                    unexportMainSymbol.getObjects().from(testedBinary.getObjects());
                    testableObjects = unexportMainSymbol.getRelocatedObjects();
                } else {
                    testableObjects = testedBinary.getObjects();
                }
                Dependency linkDependency = project.getDependencies().create(testableObjects);
                testExecutable.getLinkConfiguration().getDependencies().add(linkDependency);
            }
        });
    }
}
