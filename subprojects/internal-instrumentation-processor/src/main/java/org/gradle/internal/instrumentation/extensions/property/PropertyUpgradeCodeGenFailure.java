/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.internal.instrumentation.extensions.property;

import java.util.Collection;
import java.util.Collections;

class PropertyUpgradeCodeGenFailure extends RuntimeException {
    private final Collection<String> reasons;

    PropertyUpgradeCodeGenFailure(String reason) {
        this.reasons = Collections.singletonList(reason);
    }

    PropertyUpgradeCodeGenFailure(Collection<String> reasons) {
        assert !reasons.isEmpty();
        this.reasons = reasons;
    }

    public Collection<String> getReasons() {
        return reasons;
    }
}
