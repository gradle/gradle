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
package org.gradle.jvm.internal.resolve;

import com.google.common.base.Joiner;
import com.google.common.collect.HashMultimap;
import org.gradle.api.internal.resolve.LibraryResolutionErrorMessageBuilder;
import org.gradle.model.internal.manage.schema.ModelSchemaStore;
import org.gradle.platform.base.Binary;
import org.gradle.platform.base.BinarySpec;
import org.gradle.platform.base.internal.BinarySpecInternal;
import org.gradle.util.TextUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

public class DefaultLibraryResolutionErrorMessageBuilder implements LibraryResolutionErrorMessageBuilder {
    private static final String TARGET_PLATFORM = "targetPlatform";

    private final VariantsMetaData variantsMetaData;
    private final ModelSchemaStore schemaStore;
    private final Set<String> variantAxesToResolve;

    public DefaultLibraryResolutionErrorMessageBuilder(VariantsMetaData variantsMetaData, ModelSchemaStore schemaStore) {
        this.variantsMetaData = variantsMetaData;
        this.schemaStore = schemaStore;
        this.variantAxesToResolve = variantsMetaData.getNonNullVariantAxes();
    }

    @Override
    public String multipleCompatibleVariantsErrorMessage(String libraryName, Iterable<? extends Binary> binaries) {
        List<String> variantDescriptors = new ArrayList<String>();
        StringBuilder variantDescriptor = new StringBuilder();
        for (Binary binary : binaries) {
            BinarySpec variant = (BinarySpec) binary;
            variantDescriptor.setLength(0);
            boolean first = true;
            variantDescriptor.append("    - ").append(variant.getDisplayName()).append(" [");
            VariantsMetaData metaData = DefaultVariantsMetaData.extractFrom(variant, schemaStore.getSchema(((BinarySpecInternal)variant).getPublicType()));

            for (String axis : metaData.getNonNullVariantAxes()) {
                if (first) {
                    first = false;
                } else {
                    variantDescriptor.append(", ");
                }
                variantDescriptor.append(renderAxisName(axis)).append(":'").append(metaData.getValueAsString(axis)).append("'");
            }
            variantDescriptor.append(TextUtil.toPlatformLineSeparators("]\n"));
            variantDescriptors.add(variantDescriptor.toString());
        }
        StringBuilder sb = new StringBuilder(String.format(TextUtil.toPlatformLineSeparators("Multiple compatible variants found for library '%s':\n"), libraryName));
        for (String descriptor : variantDescriptors) {
            sb.append(descriptor);
        }
        return sb.toString();
    }

    @Override
    public String noCompatibleVariantErrorMessage(String libraryName, Iterable<? extends Binary> allBinaries) {
        HashMultimap<String, String> variantAxisMessages = HashMultimap.create();
        for (Binary binary : allBinaries) {
            BinarySpec spec = (BinarySpec) binary;
            VariantsMetaData md = DefaultVariantsMetaData.extractFrom(spec, schemaStore.getSchema(((BinarySpecInternal)spec).getPublicType()));
            Set<String> variantAxesWithIncompatibleTypes = VariantsMetaDataHelper.determineAxesWithIncompatibleTypes(variantsMetaData, md, variantAxesToResolve);
            for (String variantAxis : variantAxesToResolve) {
                String value = md.getValueAsString(variantAxis);
                if (value != null) {
                    String message = String.format("'%s'", value);
                    if (variantAxesWithIncompatibleTypes.contains(variantAxis)) {
                        message = String.format("%s but with an incompatible type (expected '%s' was '%s')", message, variantsMetaData.getVariantAxisType(variantAxis).getConcreteClass().getName(), md.getVariantAxisType(variantAxis).getConcreteClass().getName());
                    }
                    variantAxisMessages.put(variantAxis, message);
                }
            }
        }

        Joiner joiner = Joiner.on(", ").skipNulls();
        StringBuilder error = new StringBuilder(String.format(TextUtil.toPlatformLineSeparators("Cannot find a compatible variant for library '%s'.\n"), libraryName));
        for (String variantAxis : variantAxesToResolve) {
            String axisName = renderAxisName(variantAxis);
            error.append("    Required ").append(axisName).append(" '").append(variantsMetaData.getValueAsString(variantAxis)).append("'");
            Set<String> available = new TreeSet<String>(variantAxisMessages.get(variantAxis));
            if (!available.isEmpty()) {
                error.append(", available: ").append(joiner.join(available)).append("\n");
            } else {
                error.append(" but no compatible variant was found\n");
            }
        }
        return error.toString();
    }

    private String renderAxisName(String variantAxis) {
        return TARGET_PLATFORM.equals(variantAxis) ? "platform" : variantAxis;
    }
}
