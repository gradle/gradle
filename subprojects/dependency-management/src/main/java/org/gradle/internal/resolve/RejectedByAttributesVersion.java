/*
 * Copyright 2018 the original author or authors.
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
package org.gradle.internal.resolve;

import com.google.common.base.Objects;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.internal.attributes.AttributeValue;
import org.gradle.internal.component.model.AttributeMatcher;
import org.gradle.internal.logging.text.TreeFormatter;

import java.util.Comparator;
import java.util.List;

public class RejectedByAttributesVersion extends RejectedVersion {
    private static final Comparator<AttributeMatcher.MatchingDescription<?>> DESCRIPTION_COMPARATOR = Comparator.comparing(o -> o.getRequestedAttribute().getName());
    private final List<AttributeMatcher.MatchingDescription<?>> matchingDescription;

    public RejectedByAttributesVersion(ModuleComponentIdentifier id, List<AttributeMatcher.MatchingDescription<?>> matchingDescription) {
        super(id);
        this.matchingDescription = matchingDescription;
    }

    @Override
    public void describeTo(TreeFormatter builder) {
        matchingDescription.sort(DESCRIPTION_COMPARATOR);
        builder.node(getId().getVersion());
        builder.startChildren();
        for (AttributeMatcher.MatchingDescription<?> description : matchingDescription) {
            builder.node("Attribute '" + description.getRequestedAttribute().getName() + "'");
            if (description.isMatch()) {
                builder.append(" matched. ");
            } else {
                builder.append(" didn't match. ");
            }
            builder.append("Requested " + prettify(description.getRequestedValue()) + ", was: " + prettify(description.getFound()));
        }
        builder.endChildren();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        RejectedByAttributesVersion that = (RejectedByAttributesVersion) o;
        return Objects.equal(getId(), that.getId());
    }

    @Override
    public int hashCode() {
        return getId().hashCode();
    }

    private static String prettify(AttributeValue<?> value) {
        if (value.isPresent()) {
            return "'" + value.get() + "'";
        } else {
            return "not found";
        }
    }

    public List<AttributeMatcher.MatchingDescription<?>> getMatchingDescription() {
        return matchingDescription;
    }
}
