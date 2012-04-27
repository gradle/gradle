/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.api.tasks.testing;

import org.gradle.util.GUtil;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;

public class TestExceptionLogging {
    private boolean enabled;
    private Set<StackTraceFilter> stackTraceFilters = EnumSet.of(StackTraceFilter.TRUNCATE);
    private PackageFormat packageFormat = PackageFormat.SHORT;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean flag) {
        enabled = flag;
    }

    public void enabled(boolean flag) {
        enabled = flag;
    }

    public Set<StackTraceFilter> getStackTraceFilters() {
        return stackTraceFilters;
    }

    public void setStackTraceFilters(Set<Object> filters) {
        stackTraceFilters = convertStackTraceFilters(filters);
    }

    public void stackTraceFilters(Object... filters) {
        stackTraceFilters.addAll(convertStackTraceFilters(Arrays.asList(filters)));
    }

    public PackageFormat getPackageFormat() {
        return packageFormat;
    }

    public void setPackageFormat(Object packageFormat) {
        this.packageFormat = convertPackageFormat(packageFormat);
    }

    public void packageFormat(Object packageFormat) {
        this.packageFormat = convertPackageFormat(packageFormat);
    }

    private Set<StackTraceFilter> convertStackTraceFilters(Iterable<Object> filters) {
        Set<StackTraceFilter> converted = EnumSet.noneOf(StackTraceFilter.class);
        for (Object filter : filters) {
            if (filter instanceof StackTraceFilter) {
                converted.add((StackTraceFilter) filter);
            } else {
                converted.add(StackTraceFilter.valueOf(GUtil.toConstant(filter.toString())));
            }
        }
        return converted;
    }

    private PackageFormat convertPackageFormat(Object packageFormat) {
        return PackageFormat.valueOf(GUtil.toConstant(packageFormat.toString()));
    }
}
