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

package org.gradle.nativeplatform.platform.internal;

public abstract class ImmutableDefaultNativePlatform extends DefaultNativePlatform {

    public ImmutableDefaultNativePlatform(String name, OperatingSystemInternal operatingSystem, ArchitectureInternal architecture) {
        super(name, operatingSystem, architecture);
    }

    @Override
    public void operatingSystem(String name) {
        throw new UnsupportedOperationException("The operatingSystem cannot be changed.");
    }

    @Override
    public void architecture(String name) {
        throw new UnsupportedOperationException("The architecture cannot be changed.");
    }

}
