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

import com.google.common.base.Joiner;
import com.google.common.collect.HashMultimap;
import org.gradle.language.base.internal.model.DefaultVariantsMetaData;
import org.gradle.language.base.internal.model.VariantsMetaData;
import org.gradle.language.base.internal.model.VariantsMetaDataHelper;
import org.gradle.model.internal.manage.schema.ModelSchemaStore;
import org.gradle.platform.base.BinarySpec;

import java.util.*;

public class DefaultLibraryResolutionErrorMessageBuilder implements LibraryResolutionErrorMessageBuilder {
    private static final String TARGET_PLATFORM = "targetPlatform";

    private final VariantsMetaData variantsMetaData;
    private final ModelSchemaStore schemaStore;
    private final Set<String> resolveDimensions;

    public DefaultLibraryResolutionErrorMessageBuilder(VariantsMetaData variantsMetaData, ModelSchemaStore schemaStore) {
        this.variantsMetaData = variantsMetaData;
        this.schemaStore = schemaStore;
        this.resolveDimensions = variantsMetaData.getNonNullDimensions();
    }

    @Override
    public String multipleCompatibleVariantsErrorMessage(String libraryName, Collection<? extends BinarySpec> binaries) {
        List<String> variantDescriptors = new ArrayList<String>(binaries.size());
        StringBuilder variantDescriptor = new StringBuilder();
        for (BinarySpec variant : binaries) {
            variantDescriptor.setLength(0);
            variantDescriptor.append("   - ").append(variant.getDisplayName()).append(":\n");
            VariantsMetaData metaData = DefaultVariantsMetaData.extractFrom(variant, schemaStore);
            Set<String> dimensions = new TreeSet<String>(metaData.getNonNullDimensions());
            if (dimensions.size() > 1) { // 1 because of targetPlatform, which has compatibility rules beyond equality
                for (String dimension : dimensions) {
                    variantDescriptor.append("       * ").append(dimension).append(" '").append(metaData.getValueAsString(dimension)).append("'\n");

                }
                variantDescriptors.add(variantDescriptor.toString());
            }
        }
        StringBuilder sb = new StringBuilder(String.format("Multiple compatible variants found for library '%s':\n", libraryName));
        for (String descriptor : variantDescriptors) {
            sb.append(descriptor);
        }
        return sb.toString();
    }

    @Override
    public String noCompatibleBinaryErrorMessage(String libraryName, Collection<BinarySpec> allBinaries) {
        HashMultimap<String, String> variants = HashMultimap.create();
        for (BinarySpec spec : allBinaries) {
            VariantsMetaData md = DefaultVariantsMetaData.extractFrom(spec, schemaStore);
            Set<String> incompatibleDimensionTypes = VariantsMetaDataHelper.incompatibleDimensionTypes(variantsMetaData, md, resolveDimensions);
            for (String dimension : resolveDimensions) {
                String value = md.getValueAsString(dimension);
                if (value != null) {
                    String message = String.format("'%s'", value);
                    if (incompatibleDimensionTypes.contains(dimension)) {
                        message = String.format("%s but with an incompatible type (expected '%s' was '%s')", message, variantsMetaData.getDimensionType(dimension).getConcreteClass().getName(), md.getDimensionType(dimension).getConcreteClass().getName());
                    }
                    variants.put(dimension, message);
                }
            }
        }

        Joiner joiner = Joiner.on(", ").skipNulls();
        StringBuilder error = new StringBuilder(String.format("Cannot find a compatible variant for library '%s'.\n", libraryName));
        for (String dimension : resolveDimensions) {
            String dimensionName = isPlatformDimension(dimension) ? "platform" : dimension;
            error.append("    Required ").append(dimensionName).append(" '").append(variantsMetaData.getValueAsString(dimension)).append("'");
            Set<String> available = new TreeSet<String>(variants.get(dimension));
            if (!available.isEmpty()) {
                error.append(", available: ").append(joiner.join(available)).append("\n");
            } else {
                error.append(" but no compatible variant was found\n");
            }
        }
        return error.toString();
    }

    private static boolean isPlatformDimension(String dimension) {
        return TARGET_PLATFORM.equals(dimension);
    }

}
