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

import com.google.common.base.Objects;
import com.google.common.collect.Lists;
import org.gradle.api.artifacts.component.LibraryBinaryIdentifier;
import org.gradle.api.internal.resolve.VariantBinarySelector;
import org.gradle.model.internal.manage.schema.ModelSchemaStore;
import org.gradle.platform.base.Binary;
import org.gradle.platform.base.BinarySpec;
import org.gradle.platform.base.VariantComponent;
import org.gradle.platform.base.internal.BinarySpecInternal;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class JvmVariantSelector implements VariantBinarySelector {
    private final VariantsMatcher variantsMatcher;
    private final VariantsMetaData variantsMetaData;

    public JvmVariantSelector(List<VariantAxisCompatibilityFactory> factories, Class<? extends BinarySpec> binarySpecType, ModelSchemaStore schemaStore, VariantsMetaData variantsMetaData) {
        this.variantsMatcher = new VariantsMatcher(factories, binarySpecType, schemaStore);
        this.variantsMetaData = variantsMetaData;
    }

    @Override
    public Collection<? extends BinarySpec> selectVariants(VariantComponent componentSpec, @Nullable String requestedVariant) {
        // We know that Jvm binaries are BinarySpec instances
        Collection<BinarySpec> allBinaries = Lists.newArrayList();
        for (Binary binary : componentSpec.getVariants()) {
            allBinaries.add((BinarySpec) binary);
        }
        if (requestedVariant != null) {
            // Choose explicit variant
            for (Binary binarySpec : allBinaries) {
                BinarySpecInternal binary = (BinarySpecInternal) binarySpec;
                LibraryBinaryIdentifier id = binary.getId();
                if (Objects.equal(requestedVariant, id.getVariant())) {
                    return Collections.singleton(binary);
                }
            }
            return Collections.emptySet();
        }

        return variantsMatcher.filterBinaries(variantsMetaData, allBinaries);
    }
}
