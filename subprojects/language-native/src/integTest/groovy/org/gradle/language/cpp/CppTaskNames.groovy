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

package org.gradle.language.cpp

import org.gradle.language.LanguageTaskNames

abstract trait CppTaskNames extends LanguageTaskNames {
    @Override
    String getLanguageTaskSuffix() {
        return 'Cpp'
    }

    static Map<String, VariantDimension> toVariantContext(Object[] collections) {
        def result = new HashMap<String, VariantDimension>().withDefault { VariantDimension.missing(it) }
        def dimensions = GroovyCollections.combinations(collections)
        dimensions.eachWithIndex { item, idx ->
            if (item instanceof VariantDimension) {
                assert collections[idx] instanceof Iterable
                if (collections[idx].size() > 1) {
                    result.put(item.dimensionName, item)
                }
            } else if (item instanceof Map) {
                item.each { key, value ->
                    assert key instanceof String
                    assert value instanceof String
                    if (collections[idx].collect { it.get(key) }.unique().size() > 1) {
                        result.put(key, VariantDimension.of(key, value))
                    }
                }
            }
        }

        return result
    }

    static abstract class VariantDimension {
        final String dimensionName

        protected VariantDimension(String dimensionName) {
            this.dimensionName = dimensionName
        }

        abstract String getAsPath()
        abstract String getAsPublishingName()
        abstract String getAsVariantName()
        abstract String getName()

        static VariantDimension missing(String dimensionName) {
            return new MissingVariantDimension(dimensionName)
        }

        static VariantDimension of(String dimensionName, String dimensionValue) {
            return new DefaultVariantDimension(dimensionName, dimensionValue.toLowerCase())
        }

        static class DefaultVariantDimension extends VariantDimension {
            private final String normalizedDimensionValue

            private DefaultVariantDimension(String dimensionName, String normalizedDimensionValue) {
                super(dimensionName)
                this.normalizedDimensionValue = normalizedDimensionValue
            }

            @Override
            String getAsPath() {
                return "/${normalizedDimensionValue}"
            }

            @Override
            String getAsPublishingName() {
                return "_${normalizedDimensionValue.replace("-", "_")}"
            }

            @Override
            String getAsVariantName() {
                return normalizedDimensionValue.capitalize()
            }

            @Override
            String getName() {
                return normalizedDimensionValue
            }
        }

        static class MissingVariantDimension extends VariantDimension {
            final String asPath = ""
            final String asPublishingName = ""
            final String asVariantName = ""
            final String name = ""

            private MissingVariantDimension(String dimensionName) {
                super(dimensionName)
            }
        }
    }
}
