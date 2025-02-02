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

package org.gradle.jvm.toolchain.internal;

import org.gradle.api.internal.provider.PropertyFactory;
import org.gradle.internal.jvm.Jvm;
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;
import org.gradle.jvm.toolchain.JavaLanguageVersion;

import javax.inject.Inject;

@ServiceScope(Scope.Global.class)
public abstract class CurrentJvmToolchainSpec extends DefaultToolchainSpec {

    @Inject
    public CurrentJvmToolchainSpec(PropertyFactory propertyFactory, Jvm currentJvm) {
        super(propertyFactory);
        getLanguageVersion().set(JavaLanguageVersion.of(currentJvm.getJavaVersion().getMajorVersion()));

        // disallow changing property values
        finalizeProperties();
    }

    @Override
    public String getDisplayName() {
        return "CurrentJVM " + super.getDisplayName();
    }
}
