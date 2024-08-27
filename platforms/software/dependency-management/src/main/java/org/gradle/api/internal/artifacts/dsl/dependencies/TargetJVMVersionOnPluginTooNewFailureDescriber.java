/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.api.internal.artifacts.dsl.dependencies;

import org.gradle.api.JavaVersion;
import org.gradle.api.attributes.java.TargetJvmVersion;
import org.gradle.api.attributes.plugin.GradlePluginApiVersion;
import org.gradle.internal.component.resolution.failure.describer.ResolutionFailureDescriber;
import org.gradle.internal.component.resolution.failure.exception.AbstractResolutionFailureException;
import org.gradle.internal.component.resolution.failure.exception.VariantSelectionByAttributesException;
import org.gradle.internal.component.resolution.failure.interfaces.ResolutionFailure;
import org.gradle.internal.component.resolution.failure.type.NoCompatibleVariantsFailure;

import java.util.List;

/**
 * A {@link ResolutionFailureDescriber} that describes a {@link ResolutionFailure} caused by a requested <strong>plugin</strong>
 * requiring a higher JVM version than the current build is using.
 *
 * This is determined by assessing the incompatibility of the {@link TargetJvmVersion#TARGET_JVM_VERSION_ATTRIBUTE}
 * attribute on the requested plugin and comparing the provided JVM version with the current JVM version.  Note that the requested
 * JVM version is <strong>NOT</strong> relevant to this failure - it's about an incompatibility
 * due to the candidate plugin's JVM version being too high to work on this JVM, regardless of the requested version.
 *
 * Whether a request is a plugin request or not is determined by the presence of the {@link GradlePluginApiVersion#GRADLE_PLUGIN_API_VERSION_ATTRIBUTE}
 * attribute.
 */
public abstract class TargetJVMVersionOnPluginTooNewFailureDescriber extends AbstractJVMVersionTooNewFailureDescriber {
    private static final String JVM_VERSION_TOO_HIGH_TEMPLATE = "Dependency requires at least JVM runtime version %s. This build uses a Java %s JVM.";

    private final JavaVersion currentJVMVersion = JavaVersion.current();

    @Override
    protected JavaVersion getJVMVersion(NoCompatibleVariantsFailure failure) {
        return currentJVMVersion;
    }

    @Override
    public boolean canDescribeFailure(NoCompatibleVariantsFailure failure) {
        boolean isPluginRequest = failure.getRequestedAttributes().contains(GradlePluginApiVersion.GRADLE_PLUGIN_API_VERSION_ATTRIBUTE);
        return isPluginRequest && isDueToJVMVersionTooNew(failure);
    }

    @Override
    public AbstractResolutionFailureException describeFailure(NoCompatibleVariantsFailure failure) {
        JavaVersion minJVMVersionSupported = findMinJVMSupported(failure.getCandidates()).orElseThrow(IllegalStateException::new);
        String message = buildNeedsNewerJDKFailureMsg(minJVMVersionSupported);
        List<String> resolutions = buildResolutions(suggestUpdateJVM(minJVMVersionSupported));
        return new VariantSelectionByAttributesException(message, failure, resolutions);
    }

    private String buildNeedsNewerJDKFailureMsg(JavaVersion minRequiredJVMVersion) {
        return String.format(JVM_VERSION_TOO_HIGH_TEMPLATE, minRequiredJVMVersion.getMajorVersion(), currentJVMVersion.getMajorVersion());
    }

    private String suggestUpdateJVM(JavaVersion minRequiredJVMVersion) {
        return "Run this build using a Java " + minRequiredJVMVersion.getMajorVersion() + " or newer JVM.";
    }
}
