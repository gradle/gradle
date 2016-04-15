/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.internal.logging.console;

import org.gradle.util.GUtil;

public class ProgressOperation {

    private final String shortDescription;
    private String status;
    private ProgressOperation parent;

    public ProgressOperation(String shortDescription, String status, ProgressOperation parent) {
        this.shortDescription = shortDescription;
        this.status = status;
        this.parent = parent;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getMessage() {
        if (GUtil.isTrue(status)) {
            return status;
        }
        if (GUtil.isTrue(shortDescription)) {
            return shortDescription;
        }
        return null;
    }

    public ProgressOperation getParent() {
        return parent;
    }
}
