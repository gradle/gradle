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

package org.gradle.internal.component.external.model;

import javax.annotation.Nullable;

/**
 * This is a deprecated internal class that exists specifically to be used by the
 * Android and Kotlin Androind plugins, which expect this type.
 * <p>
 * Be careful to not confuse it with the {@link org.gradle.api.internal.capabilities.ImmutableCapability ImmutableCapability}
 * interface.
 *
 * @deprecated Use {@link DefaultImmutableCapability} instead.
 */
@Deprecated
public class ImmutableCapability extends DefaultImmutableCapability {
    public ImmutableCapability(String group, String name, @Nullable String version) {
        super(group, name, version);
    }
}
