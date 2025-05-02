/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.model.internal.core.rule.describe;

import javax.annotation.concurrent.ThreadSafe;

import com.google.errorprone.annotations.FormatMethod;
import com.google.errorprone.annotations.FormatString;
import org.gradle.api.internal.cache.StringInterner;

@ThreadSafe
abstract class AbstractModelRuleDescriptor implements ModelRuleDescriptor {

    protected final static StringInterner STRING_INTERNER = new StringInterner();

    @Override
    public ModelRuleDescriptor append(ModelRuleDescriptor child) {
        return new NestedModelRuleDescriptor(this, child);
    }

    @Override
    public ModelRuleDescriptor append(String child) {
        return append(new SimpleModelRuleDescriptor(child));
    }

    @Override
    @FormatMethod
    public ModelRuleDescriptor append(@FormatString String child, Object... args) {
        return append(String.format(child, args));
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        describeTo(sb);
        return sb.toString();
    }
}
