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

package org.gradle.language.nativeplatform.internal.incremental.sourceparser;

import org.gradle.language.nativeplatform.internal.IncludeType;

/**
 * A macro function whose body cannot be resolved.
 */
public class UnresolvableMacro extends AbstractMacro {
    public UnresolvableMacro(String name) {
        super(name);
    }

    @Override
    public IncludeType getType() {
        return IncludeType.OTHER;
    }

    @Override
    public String getValue() {
        return null;
    }
}
