/*
 * Copyright 2020 the original author or authors.
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

package gradlebuild.codenarc.rules;

import org.codenarc.rule.AbstractAstVisitorRule;

public class IntegrationTestFixturesRule extends AbstractAstVisitorRule {

    @Override
    public String getName() {
        return "IntegrationTestFixtures";
    }

    @Override
    public int getPriority() {
        return 1;
    }

    @Override
    public void setName(String name) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setPriority(int priority) {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getDescription() {
        return "Reports incorrect usages of integration test fixtures";
    }

    @Override
    protected Class<?> getAstVisitorClass() {
        return IntegrationTestFixtureVisitor.class;
    }
}
