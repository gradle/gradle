/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.language

class VariantContext {
    private final Map<String, VariantDimension> dimensions

    private VariantContext() {
        this([:])
    }

    private VariantContext(Map<String, VariantDimension> dimensions) {
        this.dimensions = dimensions.withDefault { VariantDimension.missing(it) }
    }

    VariantDimension getBuildType() {
        return dimensions.get("buildType")
    }

    VariantDimension getOs() {
        return dimensions.get("os")
    }

    String getAsPath() {
        if (dimensions.isEmpty()) {
            return ''
        }
        return dimensions.values()*.name.join('/') + '/'
    }

    String getAsVariantName() {
        return dimensions.values()*.name*.capitalize().join('').uncapitalize()
    }

    String getAsPublishName() {
        if (dimensions.isEmpty()) {
            return ''
        }
        return '_' + dimensions.values()*.name.join('_').replaceAll('-', '_')
    }

    static VariantContext of(Map<String, String> dimensions = [:]) {
        return new VariantContext(dimensions.collectEntries([:]) { key, value ->
            [(key): VariantDimension.of(key, value)]
        })
    }

    static List<VariantContext> from(Object[] listOfDimensionArrays) {
        def result = new ArrayList<VariantContext>()
        def dimensions = GroovyCollections.combinations(listOfDimensionArrays)
        dimensions.each {
            def dimension = new LinkedHashMap<String, VariantDimension>()
            it.eachWithIndex { item, idx ->
                if (item instanceof VariantDimension) {
                    assert listOfDimensionArrays[idx] instanceof Iterable
                    if (listOfDimensionArrays[idx].size() > 1) {
                        dimension.put(item.dimensionName, item)
                    }
                } else if (item instanceof Map) {
                    item.each { key, value ->
                        assert key instanceof String
                        assert value instanceof String
                        if (listOfDimensionArrays[idx].collect { it.get(key) }.unique().size() > 1) {
                            dimension.put(key, VariantDimension.of(key, value))
                        }
                    }
                }
            }
            result.add(new VariantContext(dimension))
        }

        return result
    }

    static List<VariantDimension> dimensions(String dimensionName, List<String> dimensionValues) {
        return VariantDimension.of(dimensionName, dimensionValues)
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

        static List<VariantDimension> of(String dimensionName, List<String> dimensionValues) {
            return dimensionValues.collect { of(dimensionName, it) }
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
