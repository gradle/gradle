/*
 * Copyright 2011 the original author or authors.
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

package org.gradle.tooling.model.internal;

import org.gradle.tooling.UnknownModelException;
import org.gradle.tooling.UnsupportedVersionException;
import org.gradle.tooling.internal.consumer.Distribution;
import org.gradle.tooling.internal.consumer.versioning.ModelMapping;
import org.gradle.tooling.internal.protocol.InternalUnsupportedModelException;
import org.gradle.tooling.model.UnsupportedMethodException;

public class Exceptions {

    public final static String INCOMPATIBLE_VERSION_HINT =
            "Most likely the model of that type is not supported in the target Gradle version."
                    + "\nTo resolve the problem you can change/upgrade the Gradle version the tooling api connects to.";

    public static UnsupportedMethodException unsupportedMethod(String method) {
        return new UnsupportedMethodException(formatUnsupportedModelMethod(method));
    }

    private static String formatUnsupportedModelMethod(String method) {
        return String.format("Unsupported method: %s."
                + "\nThe version of Gradle you connect to does not support that method."
                + "\nTo resolve the problem you can change/upgrade the target version of Gradle you connect to."
                + "\nAlternatively, you can ignore this exception and read other information from the model.",
            method);
    }

    public static UnknownModelException unsupportedModel(Class<?> modelType, String targetVersion) {
        ModelMapping modelMapping = new ModelMapping();
        String versionAdded = modelMapping.getVersionAdded(modelType);
        if (versionAdded != null) {
            return new UnknownModelException(String.format("The version of Gradle you are using (%s) does not support building a model of type '%s'. Support for building '%s' models was added in Gradle %s and is available in all later versions.",
                    targetVersion, modelType.getSimpleName(), modelType.getSimpleName(), versionAdded));
        } else {
            return new UnknownModelException(String.format("The version of Gradle you are using (%s) does not support building a model of type '%s'. Support for building custom tooling models was added in Gradle 1.6 and is available in all later versions.",
                    targetVersion, modelType.getSimpleName()));
        }
    }

    public static UnknownModelException unknownModel(Class<?> type, InternalUnsupportedModelException failure) {
        return new UnknownModelException(String.format("No model of type '%s' is available in this build.", type.getSimpleName()), failure.getCause());
    }

    public static UnsupportedVersionException unsupportedFeature(String feature, Distribution distro, String versionAdded) {
        return new UnsupportedVersionException(String.format("The version of Gradle you are using (%s) does not support the %s. Support for this is available in Gradle %s and all later versions.",
                distro.getDisplayName(), feature, versionAdded));
    }

    public static UnsupportedVersionException unsupportedFeature(String feature, String targetVersion, String versionAdded) {
        return new UnsupportedVersionException(String.format("The version of Gradle you are using (%s) does not support the %s. Support for this is available in Gradle %s and all later versions.",
                targetVersion, feature, versionAdded));
    }
}
