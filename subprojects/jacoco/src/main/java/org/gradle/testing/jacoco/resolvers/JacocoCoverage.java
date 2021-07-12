/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.testing.jacoco.resolvers;

import org.gradle.api.Action;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.attributes.Category;
import org.gradle.api.attributes.DocsType;
import org.gradle.api.attributes.Usage;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.resolvers.ResolverSpec;
import org.gradle.testing.jacoco.tasks.JacocoAggregatedReport;

public abstract class JacocoCoverage extends ResolverSpec {
    private String testTaskName = "test";

    public JacocoCoverage(ObjectFactory objectFactory) {
        super(objectFactory);
    }

    public void forTestTasksNamed(String testTaskName) {
        this.testTaskName = testTaskName;
    }

    @Override
    public Action<? super AttributeContainer> getAttributes() {
        return a -> {
            a.attribute(Usage.USAGE_ATTRIBUTE, objectFactory.named(Usage.class, Usage.JAVA_RUNTIME));
            a.attribute(Category.CATEGORY_ATTRIBUTE, objectFactory.named(Category.class, Category.DOCUMENTATION));
            a.attribute(DocsType.DOCS_TYPE_ATTRIBUTE, objectFactory.named(DocsType.class, "jacoco-coverage-data"));
            a.attribute(JacocoAggregatedReport.TestCategory.ATTRIBUTE, objectFactory.named(JacocoAggregatedReport.TestCategory.class, testTaskName));
        };
    }
}
