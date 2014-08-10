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

package org.gradle.model.internal.registry;

import org.gradle.api.GradleException;

import static org.gradle.internal.SystemProperties.getLineSeparator;

public class UnboundModelRulesException extends GradleException {

    public UnboundModelRulesException(Iterable<RuleBinder<?>> bindings) {
        super(toMessage(bindings));
    }

    private static String toMessage(Iterable<RuleBinder<?>> bindings) {
        StringBuilder sb = new StringBuilder("The following model rules are unbound:").append(getLineSeparator());
        for (RuleBinder<?> binding : bindings) {
            sb.append("  ");
            binding.getDescriptor().describeTo(sb);
            sb.append(getLineSeparator());

            // TODO details of what is unbound
        }
        return sb.toString();
    }

}
