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

public class TestExceptionLogging {
    private boolean enabled;
    private StackTraceFormat stackTraceFormat = StackTraceFormat.TRUNCATED;
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

    public StackTraceFormat getStackTraceFormat() {
        return stackTraceFormat;
    }

    public void setStackTraceFormat(Object stackTraceFormat) {
        this.stackTraceFormat = convertFormat(stackTraceFormat);
    }

    public void stackTraceFormat(Object format) {
        this.stackTraceFormat = convertFormat(format);
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

    private StackTraceFormat convertFormat(Object format) {
        return StackTraceFormat.valueOf(GUtil.toConstant(format.toString()));
    }

    private PackageFormat convertPackageFormat(Object packageFormat) {
        return PackageFormat.valueOf(GUtil.toConstant(packageFormat.toString()));
    }
}
