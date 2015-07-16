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
import com.google.common.base.Predicate;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Ordering;
import org.gradle.api.artifacts.component.LibraryComponentSelector;
import org.gradle.jvm.JarBinarySpec;
import org.gradle.jvm.JvmLibrarySpec;
import org.gradle.jvm.platform.JavaPlatform;
import org.gradle.language.base.internal.model.DefaultVariantsMetaData;
import org.gradle.language.base.internal.model.VariantsMetaData;
import org.gradle.language.base.internal.model.VariantsMetaDataHelper;
import org.gradle.platform.base.BinarySpec;
import org.gradle.platform.base.LibrarySpec;
import org.gradle.platform.base.Platform;

import java.util.*;

public class LibraryResolutionErrorMessageBuilder {
    private static final String TARGET_PLATFORM = "targetPlatform";

    private final VariantsMetaData variantsMetaData;
    private final Platform platform;
    private final Set<String> resolveDimensions;

    public LibraryResolutionErrorMessageBuilder(VariantsMetaData variantsMetaData) {
        this.variantsMetaData = variantsMetaData;
        this.platform = variantsMetaData.getValueAsType(Platform.class, TARGET_PLATFORM);
        this.resolveDimensions = variantsMetaData.getNonNullDimensions();
    }

    public static List<String> formatLibraryNames(List<String> libs) {
        List<String> list = Lists.transform(libs, new Function<String, String>() {
            @Override
            public String apply(String input) {
                return String.format("'%s'", input);
            }
        });
        return Ordering.natural().sortedCopy(list);
    }

    public String multipleBinariesForSameVariantErrorMessage(String libraryName, Collection<? extends BinarySpec> binaries) {
        List<String> binaryDescriptors = new ArrayList<String>(binaries.size());
        StringBuilder binaryDescriptor = new StringBuilder();
        for (BinarySpec variant : binaries) {
            binaryDescriptor.setLength(0);
            binaryDescriptor.append("   - ").append(variant.getDisplayName()).append(":\n");
            VariantsMetaData metaData = DefaultVariantsMetaData.extractFrom(variant);
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

    private static boolean isPlatformDimension(String dimension) {
        return TARGET_PLATFORM.equals(dimension);
    }

    public String noCompatibleBinaryErrorMessage(String libraryName, Collection<BinarySpec> allBinaries) {
        if (resolveDimensions.size() == 1) { // 1 because of targetPlatform
            List<String> availablePlatforms = Lists.transform(
                Lists.newArrayList(allBinaries), new Function<BinarySpec, String>() {
                    @Override
                    public String apply(BinarySpec input) {
                        return input instanceof JarBinarySpec ? ((JarBinarySpec) input).getTargetPlatform().toString() : input.toString();
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
                        if (input instanceof JarBinarySpec) {
                            JavaPlatform targetPlatform = ((JarBinarySpec) input).getTargetPlatform();
                            if (moreThanOneBinary) {
                                return String.format("'%s' on %s", targetPlatform.getName(), input.getDisplayName());
                            }
                            return String.format("'%s'", targetPlatform.getName(), input.getDisplayName());
                        }
                        return null;
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
                VariantsMetaData md = DefaultVariantsMetaData.extractFrom(spec);
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

    public static LibraryResolutionResult resultOf(Collection<? extends LibrarySpec> libraries, String libraryName, Predicate<? super LibrarySpec> libraryFilter) {
        LibraryResolutionResult result = new LibraryResolutionResult();
        for (LibrarySpec librarySpec : libraries) {
            if (libraryFilter.apply(librarySpec)) {
                result.libsMatchingRequirements.put(librarySpec.getName(), librarySpec);
            } else {
                result.libsNotMatchingRequirements.put(librarySpec.getName(), librarySpec);
            }
        }
        result.resolve(libraryName);
        return result;
    }

    public static LibraryResolutionResult projectNotFound() {
        return LibraryResolutionResult.PROJECT_NOT_FOUND;
    }

    public static LibraryResolutionResult emptyResolutionResult() {
        return LibraryResolutionResult.EMPTY;
    }


    /**
     * Intermediate data structure used to store the result of a resolution and help at building an understandable error message in case resolution fails.
     */
    public static class LibraryResolutionResult {
        private static final LibraryResolutionResult EMPTY = new LibraryResolutionResult();
        private static final LibraryResolutionResult PROJECT_NOT_FOUND = new LibraryResolutionResult();
        private final Map<String, LibrarySpec> libsMatchingRequirements;
        private final Map<String, LibrarySpec> libsNotMatchingRequirements;

        private LibrarySpec selectedLibrary;
        private LibrarySpec nonMatchingLibrary;

        private LibraryResolutionResult() {
            this.libsMatchingRequirements = Maps.newHashMap();
            this.libsNotMatchingRequirements = Maps.newHashMap();
        }

        private LibrarySpec getSingleMatchingLibrary() {
            if (libsMatchingRequirements.size() == 1) {
                return libsMatchingRequirements.values().iterator().next();
            }
            return null;
        }

        private void resolve(String libraryName) {
            if (libraryName == null) {
                LibrarySpec singleMatchingLibrary = getSingleMatchingLibrary();
                if (singleMatchingLibrary == null) {
                    return;
                }
                libraryName = singleMatchingLibrary.getName();
            }

            selectedLibrary = libsMatchingRequirements.get(libraryName);
            nonMatchingLibrary = libsNotMatchingRequirements.get(libraryName);
        }

        public boolean isProjectNotFound() {
            return PROJECT_NOT_FOUND == this;
        }

        public boolean hasLibraries() {
            return !libsMatchingRequirements.isEmpty() || !libsNotMatchingRequirements.isEmpty();
        }

        public LibrarySpec getSelectedLibrary() {
            return selectedLibrary;
        }

        public LibrarySpec getNonMatchingLibrary() {
            return nonMatchingLibrary;
        }

        public List<String> getCandidateLibraries() {
            return Lists.newArrayList(libsMatchingRequirements.keySet());
        }

        public String toResolutionErrorMessage(
            LibraryComponentSelector selector) {
            List<String> candidateLibraries = formatLibraryNames(getCandidateLibraries());
            String projectPath = selector.getProjectPath();
            String libraryName = selector.getLibraryName();

            StringBuilder sb = new StringBuilder("Project '").append(projectPath).append("'");
            if (libraryName == null || !hasLibraries()) {
                if (isProjectNotFound()) {
                    sb.append(" not found.");
                } else if (!hasLibraries()) {
                    sb.append(" doesn't define any library.");
                } else {
                    sb.append(" contains more than one library. Please select one of ");
                    Joiner.on(", ").appendTo(sb, candidateLibraries);
                }
            } else {
                LibrarySpec notMatchingRequirements = getNonMatchingLibrary();
                if (notMatchingRequirements != null) {
                    sb.append(" contains a library named '").append(libraryName)
                        .append("' but it is not a ")
                        .append(JvmLibrarySpec.class.getSimpleName());
                } else {
                    sb.append(" does not contain library '").append(libraryName).append("'. Did you want to use ");
                    if (candidateLibraries.size() == 1) {
                        sb.append(candidateLibraries.get(0));
                    } else {
                        sb.append("one of ");
                        Joiner.on(", ").appendTo(sb, candidateLibraries);
                    }
                    sb.append("?");
                }
            }
            return sb.toString();
        }
    }
}
