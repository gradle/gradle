/*
 * Copyright 2020 the original author or authors.
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
package org.gradle.api.internal.std;

import org.gradle.api.artifacts.MutableVersionConstraint;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.ValueSource;
import org.gradle.api.provider.ValueSourceParameters;

public abstract class DependencyDataVersionConstraintValueSource implements ValueSource<MutableVersionConstraint, DependencyDataVersionConstraintValueSource.Params> {

    interface Params extends ValueSourceParameters {
        Property<DependencyData> getDependencyData();
    }

    @Override
    public MutableVersionConstraint obtain() {
        DependencyData data = getParameters().getDependencyData().get();
        return DependenciesFactoryHelper.createVersionConstraint(
            data.getRequiredVersion(),
            data.getPreferredVersion(),
            data.getStrictlyVersion(),
            data.getRejectedVersions(),
            data.getRejectAll());
    }

}
