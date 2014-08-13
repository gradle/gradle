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

package org.gradle.model.internal.report;

import com.google.common.collect.ImmutableList;
import org.gradle.model.internal.core.ModelPath;
import org.gradle.model.internal.core.ModelReference;
import org.gradle.model.internal.core.rule.describe.ModelRuleDescriptor;
import org.gradle.util.CollectionUtils;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Comparator;
import java.util.List;

public class AmbiguousBindingReporter {

    private final static String INDENT = "  ";
    private static final Comparator<Provider> PROVIDER_COMPARATOR = new Comparator<Provider>() {
        public int compare(Provider o1, Provider o2) {
            return o1.getPath().compareTo(o2.getPath());
        }
    };

    private final String referenceType;
    private final String referenceDescription;
    private final List<Provider> providers;

    public static class Provider {
        private final String description;
        private final String path;

        public Provider(String path, String description) {
            this.description = description;
            this.path = path;
        }

        public String getDescription() {
            return description;
        }

        public String getPath() {
            return path;
        }
    }

    public AmbiguousBindingReporter(ModelReference<?> reference, ModelPath path1, ModelRuleDescriptor creator1, ModelPath path2, ModelRuleDescriptor creator2) {
        this(reference.getType().toString(), reference.getDescription(), ImmutableList.of(
                new Provider(path1.toString(), creator1.toString()),
                new Provider(path2.toString(), creator2.toString())
        ));
    }

    public AmbiguousBindingReporter(String referenceType, String referenceDescription, List<Provider> providers) {
        this.referenceType = referenceType;
        this.referenceDescription = referenceDescription;
        this.providers = CollectionUtils.sort(providers, PROVIDER_COMPARATOR);
    }

    public String asString() {
        StringWriter string = new StringWriter();
        writeTo(new PrintWriter(string));
        return string.toString();
    }

    public void writeTo(PrintWriter writer) {
        //"type-only model reference of type '%s'%s is ambiguous as multiple model elements are available for this type:%n  %s (created by %s)%n  %s (created by %s)",
        writer.print("Type-only model reference of type ");
        writer.print(referenceType);
        if (referenceDescription != null) {
            writer.print(" (");
            writer.print(referenceDescription);
            writer.print(") ");
        }
        writer.println("is ambiguous as multiple model elements are available for this type:");

        boolean first = true;
        for (Provider provider : providers) {
            if (!first) {
                writer.println();
            }
            writer.print(INDENT);
            writer.print("- ");
            writer.print(provider.getPath());
            writer.print(" (created by: ");
            writer.print(provider.getDescription());
            writer.print(")");
            first = false;
        }
    }

}
