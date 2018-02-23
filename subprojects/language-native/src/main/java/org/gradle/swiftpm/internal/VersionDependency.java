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

package org.gradle.swiftpm.internal;

import javax.annotation.Nullable;
import java.net.URI;

public class VersionDependency extends Dependency {
    private final String lowerBound;
    private final String upperBound;
    private final boolean upperInclusive;

    public VersionDependency(URI url, String version) {
        super(url);
        this.lowerBound = version;
        this.upperBound = null;
        upperInclusive = false;
    }

    public VersionDependency(URI url, String lowerBound, @Nullable String upperBound, boolean upperInclusive) {
        super(url);
        this.lowerBound = lowerBound;
        this.upperBound = upperBound;
        this.upperInclusive = upperInclusive;
    }

    public String getLowerBound() {
        return lowerBound;
    }

    @Nullable
    public String getUpperBound() {
        return upperBound;
    }

    public boolean isUpperInclusive() {
        return upperInclusive;
    }
}
