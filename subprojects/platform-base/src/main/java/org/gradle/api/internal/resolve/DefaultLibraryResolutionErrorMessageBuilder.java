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
package org.gradle.api.internal.resolve;

import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Lists;
import org.gradle.language.base.internal.model.DefaultVariantsMetaData;
import org.gradle.language.base.internal.model.VariantsMetaData;
import org.gradle.language.base.internal.model.VariantsMetaDataHelper;
import org.gradle.model.internal.manage.schema.ModelSchemaStore;
import org.gradle.platform.base.BinarySpec;
import org.gradle.platform.base.Platform;

import java.util.*;

public class DefaultLibraryResolutionErrorMessageBuilder implements LibraryResolutionErrorMessageBuilder {
    private static final String TARGET_PLATFORM = "targetPlatform";

    private final VariantsMetaData variantsMetaData;
    private final ModelSchemaStore schemaStore;
    private final Platform platform;
    private final Set<String> resolveDimensions;
    private final LocalLibraryMetaDataAdapter adapter;

    public DefaultLibraryResolutionErrorMessageBuilder(VariantsMetaData variantsMetaData, ModelSchemaStore schemaStore, LocalLibraryMetaDataAdapter adapter) {
        this.variantsMetaData = variantsMetaData;
        this.schemaStore = schemaStore;
        this.adapter = adapter;
        this.platform = variantsMetaData.getValueAsType(Platform.class, TARGET_PLATFORM);
        this.resolveDimensions = variantsMetaData.getNonNullDimensions();
    }

    @Override
    public String multipleBinariesForSameVariantErrorMessage(String libraryName, Collection<? extends BinarySpec> binaries) {
        List<String> binaryDescriptors = new ArrayList<String>(binaries.size());
        StringBuilder binaryDescriptor = new StringBuilder();
        for (BinarySpec variant : binaries) {
            binaryDescriptor.setLength(0);
            binaryDescriptor.append("   - ").append(variant.getDisplayName()).append(":\n");
            VariantsMetaData metaData = DefaultVariantsMetaData.extractFrom(variant, schemaStore);
            Set<String> dimensions = new TreeSet<String>(metaData.getNonNullDimensions());
            if (dimensions.size() > 1) { // 1 because of targetPlatform
                for (String dimension : dimensions) {
                    binaryDescriptor.append("       * ").append(dimension).append(" '").append(metaData.getValueAsString(dimension)).append("'\n");

                }
                binaryDescriptors.add(binaryDescriptor.toString());
            }
        }
        if (binaryDescriptors.isEmpty()) {
            return String.format("Multiple binaries available for library '%s' (%s) : %s", libraryName, platform, binaries);
        } else {
            // custom variants on binaries
            StringBuilder sb = new StringBuilder(String.format("Multiple binaries available for library '%s' (%s) :\n", libraryName, platform));
            for (String descriptor : binaryDescriptors) {
                sb.append(descriptor);
            }
            return sb.toString();
        }
    }

    // TODO:DAZ Extract the JVM-specific logic out of here: this error message builder should be general to all library types
    @Override
    public String noCompatibleBinaryErrorMessage(String libraryName, Collection<BinarySpec> allBinaries) {
        if (resolveDimensions.size() == 1) { // 1 because of targetPlatform
            List<String> availablePlatforms = Lists.transform(
                Lists.newArrayList(allBinaries), new Function<BinarySpec, String>() {
                    @Override
                    public String apply(BinarySpec input) {
                        return getPlatformDisplayName(input);
                    }
                }
            );
            return String.format("Cannot find a compatible binary for library '%s' (%s). Available platforms: %s", libraryName, platform, availablePlatforms);
        } else {
            final boolean moreThanOneBinary = allBinaries.size() > 1;
            Set<String> availablePlatforms = new TreeSet<String>(Lists.transform(
                Lists.newArrayList(allBinaries), new Function<BinarySpec, String>() {
                    @Override
                    public String apply(BinarySpec input) {
                        String platformName = getPlatformDisplayName(input);
                        if (moreThanOneBinary) {
                            return String.format("'%s' on %s", platformName, input.getDisplayName());
                        }
                        return String.format("'%s'", platformName);
                    }
                }
            ));
            StringBuilder error = new StringBuilder(String.format("Cannot find a compatible binary for library '%s' (%s).\n", libraryName, platform));
            Joiner joiner = Joiner.on(",").skipNulls();
            error.append("    Required platform '").append(platform.getName()).append("', ");
            error.append("available: ").append(joiner.join(availablePlatforms));
            error.append("\n");
            HashMultimap<String, String> variants = HashMultimap.create();
            for (BinarySpec spec : allBinaries) {
                VariantsMetaData md = DefaultVariantsMetaData.extractFrom(spec, schemaStore);
                Set<String> incompatibleDimensionTypes = VariantsMetaDataHelper.incompatibleDimensionTypes(variantsMetaData, md, resolveDimensions);
                for (String dimension : resolveDimensions) {
                    String value = md.getValueAsString(dimension);
                    if (value != null) {
                        String message;
                        if (moreThanOneBinary) {
                            message = String.format("'%s' on %s", value, spec.getDisplayName());
                        } else {
                            message = String.format("'%s'", value);
                        }
                        if (incompatibleDimensionTypes.contains(dimension)) {
                            message = String.format("%s but with an incompatible type (expected '%s' was '%s')", message, variantsMetaData.getDimensionType(dimension).getConcreteClass().getName(), md.getDimensionType(dimension).getConcreteClass().getName());
                        }
                        variants.put(dimension, message);
                    }
                }
            }

            for (String dimension : resolveDimensions) {
                if (!isPlatformDimension(dimension)) {
                    error.append("    Required ").append(dimension).append(" '").append(variantsMetaData.getValueAsString(dimension)).append("'");
                    Set<String> available = new TreeSet<String>(variants.get(dimension));
                    if (!available.isEmpty()) {
                        error.append(", available: ").append(joiner.join(available)).append("\n");
                    } else {
                        error.append(" but no compatible binary was found\n");
                    }
                }
            }
            return error.toString();
        }
    }

    private String getPlatformDisplayName(BinarySpec input) {
        return adapter.getPlatformDisplayName(input);
    }

    private static boolean isPlatformDimension(String dimension) {
        return TARGET_PLATFORM.equals(dimension);
    }

}
