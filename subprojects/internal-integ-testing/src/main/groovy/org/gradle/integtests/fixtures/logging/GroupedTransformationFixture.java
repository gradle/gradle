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

package org.gradle.integtests.fixtures.logging;

import org.gradle.api.specs.Spec;

import java.util.ArrayList;
import java.util.List;

import static org.gradle.util.internal.CollectionUtils.filter;
import static org.gradle.util.internal.CollectionUtils.join;

public class GroupedTransformationFixture {
    private final String initialSubjectType;
    private final String subject;
    private final String transformer;
    private final List<String> outputs = new ArrayList<String>(1);

    public GroupedTransformationFixture(String initialSubjectType, String subject, String transformer) {
        this.initialSubjectType = initialSubjectType;
        this.subject = subject;
        this.transformer = transformer;
    }

    public void addOutput(String transformationOutput) {
        outputs.add(transformationOutput);
    }

    public String getInitialSubjectType() {
        return initialSubjectType;
    }

    public String getSubject() {
        return subject;
    }

    public String getTransformer() {
        return transformer;
    }

    public String getOutput() {
        List<String> nonEmptyOutputs = filter(outputs, new Spec<String>() {
            @Override
            public boolean isSatisfiedBy(String string) {
                return !string.equals("");
            }
        });
        return join("\n", nonEmptyOutputs);
    }

    @Override
    public String toString() {
        return "GroupedTransformationFixture{" +
                "initialSubjectType='" + initialSubjectType + '\'' +
                ", subject='" + subject + '\'' +
                ", transformer='" + transformer + '\'' +
                '}';
    }
}
