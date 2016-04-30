/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.nativeplatform.internal.resolve;

import org.gradle.api.Transformer;
import org.gradle.api.specs.Spec;
import org.gradle.nativeplatform.NativeLibraryBinary;
import org.gradle.nativeplatform.NativeDependencySet;
import org.gradle.nativeplatform.NativeBinarySpec;
import org.gradle.util.CollectionUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class NativeBinaryResolveResult {
    private final NativeBinarySpec target;
    private final List<NativeBinaryRequirementResolveResult> resolutions = new ArrayList<NativeBinaryRequirementResolveResult>();

    public NativeBinaryResolveResult(NativeBinarySpec target, Collection<?> libs) {
        this.target = target;
        for (Object lib : libs) {
            resolutions.add(new NativeBinaryRequirementResolveResult(lib));
        }
    }

    public NativeBinarySpec getTarget() {
        return target;
    }

    public List<NativeBinaryRequirementResolveResult> getAllResolutions() {
        return resolutions;
    }

    public List<NativeDependencySet> getAllResults() {
        return CollectionUtils.collect(getAllResolutions(), new Transformer<NativeDependencySet, NativeBinaryRequirementResolveResult>() {
            public NativeDependencySet transform(NativeBinaryRequirementResolveResult original) {
                return original.getNativeDependencySet();
            }
        });
    }

    public List<NativeLibraryBinary> getAllLibraryBinaries() {
        List<NativeLibraryBinary> result = new ArrayList<NativeLibraryBinary>();
        for (NativeBinaryRequirementResolveResult resolution : getAllResolutions()) {
            if (resolution.getLibraryBinary() != null) {
                result.add(resolution.getLibraryBinary());
            }
        }
        return result;
    }

    public List<NativeBinaryRequirementResolveResult> getPendingResolutions() {
        return CollectionUtils.filter(resolutions, new Spec<NativeBinaryRequirementResolveResult>() {
            public boolean isSatisfiedBy(NativeBinaryRequirementResolveResult element) {
                return !element.isComplete();
            }
        });
    }
}
