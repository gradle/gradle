/*
 * Copyright 2019 the original author or authors.
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
package org.gradle.internal.component.external.model;

/**
 * This special kind of capability is here only because of derived
 * variants: in case of platforms we want to make sure we can select
 * both the platform and library. For this we use different capabilities.
 * However, we still want to make sure we can select the platform component
 * whenever no explicit capability is required. In this case, and only in
 * this case, we use the "shadowed" capability to check.
 */
public interface ShadowedCapability extends CapabilityInternal {
    String getAppendix();
    CapabilityInternal getShadowedCapability();
}
