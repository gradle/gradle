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

import org.gradle.api.internal.capabilities.CapabilityInternal;
import org.gradle.api.internal.capabilities.ImmutableCapability;
import org.gradle.api.internal.capabilities.ShadowedCapability;

/**
 * A capability that is shadowed by another capability.
 * <p>
 * This class remains deeply immutable.
 */
public final class ShadowedImmutableCapability implements ShadowedCapability {
    private final ImmutableCapability shadowed;
    private final String appendix;

    public ShadowedImmutableCapability(CapabilityInternal shadowed, String appendix) {
        if (shadowed instanceof ImmutableCapability) {
            this.shadowed = (ImmutableCapability) shadowed;
        } else {
            this.shadowed = new DefaultImmutableCapability(shadowed.getGroup(), shadowed.getName(), shadowed.getVersion());
        }
        this.appendix = appendix;
    }

    @Override
    public String getAppendix() {
        return appendix;
    }

    @Override
    public ImmutableCapability getShadowedCapability() {
        return shadowed;
    }

    @Override
    public String getGroup() {
        return shadowed.getGroup();
    }

    @Override
    public String getName() {
        return shadowed.getName() + appendix;
    }

    @Override
    public String getVersion() {
        return shadowed.getVersion();
    }

    @Override
    public String getCapabilityId() {
        return shadowed.getCapabilityId() + appendix;
    }
}
