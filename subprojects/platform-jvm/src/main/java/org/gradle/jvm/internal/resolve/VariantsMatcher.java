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

import com.google.common.base.Function;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import com.google.common.collect.TreeMultimap;
import org.gradle.api.Named;
import org.gradle.internal.Cast;
import org.gradle.model.internal.manage.schema.ModelSchemaStore;
import org.gradle.platform.base.BinarySpec;
import org.gradle.platform.base.internal.BinarySpecInternal;

import java.util.*;

public class VariantsMatcher {
    private static final Comparator<VariantValue> SPEC_COMPARATOR = new Comparator<VariantValue>() {
        @Override
        public int compare(VariantValue o1, VariantValue o2) {
            return o1.spec.getDisplayName().compareTo(o2.spec.getDisplayName());
        }
    };

    private final List<VariantAxisCompatibilityFactory> factories;
    private final Class<? extends BinarySpec> binarySpecType;
    private final ModelSchemaStore schemaStore;

    public VariantsMatcher(List<VariantAxisCompatibilityFactory> factories, Class<? extends BinarySpec> binarySpecType, ModelSchemaStore schemaStore) {
        this.factories = factories;
        this.binarySpecType = binarySpecType;
        this.schemaStore = schemaStore;
    }

    private VariantAxisCompatibility<Object> createSelector(Object o) {
        for (VariantAxisCompatibilityFactory factory : factories) {
            @SuppressWarnings("unchecked")
            VariantAxisCompatibility<Object> selector = factory.getVariantAxisCompatibility(o);
            if (selector != null) {
                return selector;
            }
        }
        return new DefaultVariantAxisCompatibility();
    }

    public Collection<? extends BinarySpec> filterBinaries(VariantsMetaData variantsMetaData, Collection<BinarySpec> binaries) {
        if (binaries.isEmpty()) {
            return binaries;
        }
        Set<String> resolveDimensions = variantsMetaData.getNonNullVariantAxes();
        TreeMultimap<String, VariantValue> selectedVariants = TreeMultimap.create(String.CASE_INSENSITIVE_ORDER, SPEC_COMPARATOR);
        Set<BinarySpec> removedSpecs = Sets.newHashSet();
        for (BinarySpec binarySpec : binaries) {
            if (binarySpecType.isAssignableFrom(binarySpec.getClass())) {
                VariantsMetaData binaryVariants = DefaultVariantsMetaData.extractFrom(binarySpec, schemaStore.getSchema(((BinarySpecInternal)binarySpec).getPublicType()));
                Set<String> commonsDimensions = Sets.intersection(resolveDimensions, binaryVariants.getNonNullVariantAxes());
                Set<String> incompatibleDimensionTypes = VariantsMetaDataHelper.determineAxesWithIncompatibleTypes(variantsMetaData, binaryVariants, commonsDimensions);
                if (incompatibleDimensionTypes.isEmpty()) {
                    for (String dimension : commonsDimensions) {
                        Class<?> dimensionType = variantsMetaData.getVariantAxisType(dimension).getConcreteClass();
                        boolean isStringType = String.class == dimensionType;
                        Object requestedValue = isStringType ? variantsMetaData.getValueAsString(dimension) : variantsMetaData.getValueAsType(Cast.<Class<? extends Named>>uncheckedCast(dimensionType), dimension);
                        Object binaryValue = isStringType ? binaryVariants.getValueAsString(dimension) : binaryVariants.getValueAsType(Cast.<Class<? extends Named>>uncheckedCast(dimensionType), dimension);
                        VariantAxisCompatibility<Object> selector = createSelector(requestedValue);
                        if (selector.isCompatibleWithRequirement(requestedValue, binaryValue)) {
                            VariantValue value = new VariantValue(binaryValue, binarySpec);
                            SortedSet<VariantValue> variantValues = selectedVariants.get(dimension);
                            for (VariantValue variantValue : variantValues) {
                                // all the values are equal, but we store all the binaries that match that value
                                // and incrementally build a list of binaries which are excluded because of a better match
                                if (selector.betterFit(requestedValue, variantValue.value, binaryValue)) {
                                    // the new value is a better fit than the old one
                                    removedSpecs.add(variantValue.spec);
                                } else if (selector.betterFit(requestedValue, binaryValue, variantValue.value)) {
                                    // the old value is a better fit than the new one, let's ignore the new one altogether
                                    removedSpecs.add(value.spec);
                                }
                            }
                            selectedVariants.put(dimension, value);
                        } else {
                            removedSpecs.add(binarySpec);
                        }
                    }
                }
            }
        }

        Set<BinarySpec> union = null;
        for (String dimension : selectedVariants.keySet()) {
            Set<BinarySpec> variantValues = ImmutableSet.copyOf(Iterables.transform(selectedVariants.get(dimension), VariantValue.SPEC_FUNCTION));
            union = union == null ? variantValues : Sets.union(union, variantValues);
        }
        return union == null ? Collections.<BinarySpec>emptySet() : Sets.difference(union, removedSpecs);
    }

    private static class VariantValue {
        public static final Function<VariantValue, BinarySpec> SPEC_FUNCTION = new Function<VariantValue, BinarySpec>() {
            @Override
            public BinarySpec apply(VariantValue input) {
                return input.spec;
            }
        };

        final Object value;
        final BinarySpec spec;

        private VariantValue(Object value, BinarySpec spec) {
            this.value = value;
            this.spec = spec;
        }

        @Override
        public String toString() {
            final StringBuilder sb = new StringBuilder("VariantValue{");
            sb.append("spec=").append(spec);
            sb.append(", value=").append(value);
            sb.append('}');
            return sb.toString();
        }
    }
}
