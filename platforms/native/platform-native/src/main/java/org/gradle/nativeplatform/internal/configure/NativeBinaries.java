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
package org.gradle.nativeplatform.internal.configure;

import org.gradle.api.internal.file.FileCollectionFactory;
import org.gradle.internal.BiAction;
import org.gradle.model.ModelMap;
import org.gradle.model.internal.core.*;
import org.gradle.model.internal.core.rule.describe.SimpleModelRuleDescriptor;
import org.gradle.model.internal.manage.instance.ManagedInstance;
import org.gradle.nativeplatform.*;
import org.gradle.nativeplatform.internal.NativeBinarySpecInternal;
import org.gradle.nativeplatform.internal.resolve.NativeDependencyResolver;
import org.gradle.nativeplatform.platform.NativePlatform;
import org.gradle.platform.base.internal.BinaryNamingScheme;

public class NativeBinaries {

    public static void createNativeBinaries(
        NativeComponentSpec component,
        ModelMap<NativeBinarySpec> binaries,
        NativeDependencyResolver resolver,
        FileCollectionFactory fileCollectionFactory,
        BinaryNamingScheme namingScheme,
        NativePlatform platform,
        BuildType buildType,
        Flavor flavor
    ) {
        if (component instanceof NativeLibrarySpec) {
            createNativeBinary(SharedLibraryBinarySpec.class, binaries, resolver, fileCollectionFactory, namingScheme.withBinaryType("SharedLibrary").withRole("shared", false), platform, buildType, flavor);
            createNativeBinary(StaticLibraryBinarySpec.class, binaries, resolver, fileCollectionFactory, namingScheme.withBinaryType("StaticLibrary").withRole("static", false), platform, buildType, flavor);
        } else {
            createNativeBinary(NativeExecutableBinarySpec.class, binaries, resolver, fileCollectionFactory, namingScheme.withBinaryType("Executable").withRole("executable", true), platform, buildType, flavor);
        }
    }

    private static <T extends NativeBinarySpec> void createNativeBinary(
        Class<T> type,
        ModelMap<NativeBinarySpec> binaries,
        final NativeDependencyResolver resolver,
        final FileCollectionFactory fileCollectionFactory,
        final BinaryNamingScheme namingScheme,
        final NativePlatform platform,
        final BuildType buildType,
        final Flavor flavor
    ) {
        final String name = namingScheme.getBinaryName();
        binaries.create(name, type);

        // TODO:REUSE Refactor after removing reuse
        // This is horrendously bad.
        // We need to set the platform, _before_ the @Defaults rules of NativeBinaryRules assign the toolchain.
        // We can't just assign the toolchain here because the initializer would be closing over the toolchain which is not reusable, and this breaks model reuse.
        // So here we are just closing over the safely reusable things and then using proper dependencies for the tool chain registry.
        // Unfortunately, we can't do it in the create action because that would fire _after_ @Defaults rules.
        // We have to use a @Defaults rule to assign the tool chain because it needs to be there in user @Mutate rules
        // Or at least, the file locations do so that they can be tweaked.
        // LD - 5/6/14
        MutableModelNode backingNode = ((ManagedInstance) binaries).getBackingNode();
        ModelPath binaryPath = backingNode.getPath().child(name);
        backingNode.applyToLink(ModelActionRole.Defaults, DirectNodeNoInputsModelAction.of(
            ModelReference.of(binaryPath, NativeBinarySpec.class),
            new SimpleModelRuleDescriptor("initialize binary " + binaryPath),
            new BiAction<MutableModelNode, NativeBinarySpec>() {
                @Override
                public void execute(MutableModelNode mutableModelNode, NativeBinarySpec nativeBinarySpec) {
                    initialize(nativeBinarySpec, namingScheme, resolver, fileCollectionFactory, platform, buildType, flavor);
                }
            }
        ));
        binaries.named(name, NativeBinaryRules.class);
    }

    public static void initialize(
        NativeBinarySpec nativeBinarySpec,
        BinaryNamingScheme namingScheme,
        NativeDependencyResolver resolver,
        FileCollectionFactory fileCollectionFactory,
        NativePlatform platform,
        BuildType buildType,
        Flavor flavor
    ) {
        NativeBinarySpecInternal nativeBinary = (NativeBinarySpecInternal) nativeBinarySpec;
        nativeBinary.setNamingScheme(namingScheme);
        nativeBinary.setTargetPlatform(platform);
        nativeBinary.setBuildType(buildType);
        nativeBinary.setFlavor(flavor);
        nativeBinary.setResolver(resolver);
        nativeBinary.setFileCollectionFactory(fileCollectionFactory);
    }

}
