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

package org.gradle.language.base.internal.compile;

import org.gradle.api.NonNullApi;
import org.gradle.language.base.compile.CompilerVersion;
import org.gradle.util.VersionNumber;

@NonNullApi
public class DefaultCompilerVersion implements CompilerVersion {

    private final String type;
    private final String vendor;
    private final VersionNumber version;

    public DefaultCompilerVersion(String type, String vendor, VersionNumber version) {
        this.type = type;
        this.vendor = vendor;
        this.version = version;
    }

    @Override
    public String getType() {
        return type;
    }

    @Override
    public String getVendor() {
        return vendor;
    }

    @Override
    public String getVersion() {
        return version.toString();
    }

    @Override
    public String toString() {
        return "CompilerVersion{" + "type='" + type + '\'' + ", vendor='" + vendor + '\'' + ", version=" + version + '}';
    }
}
