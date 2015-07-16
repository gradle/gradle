/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.api.reporting.model.internal;

import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import org.gradle.model.internal.core.ModelNode;
import org.gradle.model.internal.core.rule.describe.ModelRuleDescriptor;

import java.util.List;

public class ModelReportRulesFilter {

    public static List<ModelRuleDescriptor> uniqueExecutedRulesExcludingCreator(ModelNode model) {
        final StringBuffer creatorBuffer = new StringBuffer();
        model.getDescriptor().describeTo(creatorBuffer);
        final String creator = creatorBuffer.toString();
        Iterable<ModelRuleDescriptor> filtered = Iterables.filter(model.getExecutedRules(), new Predicate<ModelRuleDescriptor>() {
            @Override
            public boolean apply(ModelRuleDescriptor input) {
                StringBuffer in = new StringBuffer();
                input.describeTo(in);
                return !in.toString().equals(creator);
            }
        });
        return ImmutableSet.copyOf(filtered).asList();
    }
}
