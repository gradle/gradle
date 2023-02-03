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

package org.gradle.api.internal.resolve;

import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.base.Predicate;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Ordering;
import org.gradle.api.artifacts.component.LibraryComponentSelector;
import org.gradle.platform.base.Binary;
import org.gradle.platform.base.VariantComponent;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Intermediate data structure used to store the result of a resolution and help at building an understandable error message in case resolution fails.
 */
public class LibraryResolutionResult {
    public static final Function<String, String> QUOTE_TRANSFORMER = new Function<String, String>() {
        @Override
        public String apply(String input) {
            return "'" + input + "'";
        }
    };
    private final Map<String, VariantComponent> libsMatchingRequirements;
    private final Map<String, VariantComponent> libsNotMatchingRequirements;
    private final Class<? extends Binary> binaryType;

    private boolean projectNotFound;

    private VariantComponent selectedLibrary;
    private VariantComponent nonMatchingLibrary;

    private LibraryResolutionResult(Class<? extends Binary> binaryType) {
        this.binaryType = binaryType;
        this.libsMatchingRequirements = Maps.newHashMap();
        this.libsNotMatchingRequirements = Maps.newHashMap();
    }

    private VariantComponent getSingleMatchingLibrary() {
        if (libsMatchingRequirements.size() == 1) {
            return libsMatchingRequirements.values().iterator().next();
        }
        return null;
    }

    private void resolve(String libraryName) {
        if (libraryName == null) {
            VariantComponent singleMatchingLibrary = getSingleMatchingLibrary();
            if (singleMatchingLibrary == null) {
                return;
            }
            libraryName = singleMatchingLibrary.getName();
        }

        selectedLibrary = libsMatchingRequirements.get(libraryName);
        nonMatchingLibrary = libsNotMatchingRequirements.get(libraryName);
    }

    public boolean isProjectNotFound() {
        return projectNotFound;
    }

    public boolean hasLibraries() {
        return !libsMatchingRequirements.isEmpty() || !libsNotMatchingRequirements.isEmpty();
    }

    public VariantComponent getSelectedLibrary() {
        return selectedLibrary;
    }

    public VariantComponent getNonMatchingLibrary() {
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
            VariantComponent notMatchingRequirements = getNonMatchingLibrary();
            if (notMatchingRequirements != null) {
                sb.append(" contains a library named '").append(libraryName)
                    .append("' but it doesn't have any binary of type ")
                    .append(binaryType.getSimpleName());
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

    public static LibraryResolutionResult of(Class<? extends Binary> binaryType, Collection<? extends VariantComponent> libraries, String libraryName, Predicate<? super VariantComponent> libraryFilter) {
        LibraryResolutionResult result = new LibraryResolutionResult(binaryType);
        for (VariantComponent librarySpec : libraries) {
            if (libraryFilter.apply(librarySpec)) {
                result.libsMatchingRequirements.put(librarySpec.getName(), librarySpec);
            } else {
                result.libsNotMatchingRequirements.put(librarySpec.getName(), librarySpec);
            }
        }
        result.resolve(libraryName);
        return result;
    }

    public static LibraryResolutionResult projectNotFound(Class<? extends Binary> binaryType) {
        LibraryResolutionResult projectNotFoundResult = new LibraryResolutionResult(binaryType);
        projectNotFoundResult.projectNotFound = true;
        return projectNotFoundResult;
    }

    public static LibraryResolutionResult emptyResolutionResult(Class<? extends Binary> binaryType) {
        return new LibraryResolutionResult(binaryType);
    }

    private static List<String> formatLibraryNames(List<String> libs) {
        List<String> list = Lists.transform(libs, QUOTE_TRANSFORMER);
        return Ordering.natural().sortedCopy(list);
    }
}
