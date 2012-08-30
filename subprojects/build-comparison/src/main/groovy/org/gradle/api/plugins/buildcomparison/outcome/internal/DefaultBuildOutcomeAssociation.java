/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.api.plugins.buildcomparison.outcome.internal;

public class DefaultBuildOutcomeAssociation<A extends BuildOutcome> implements BuildOutcomeAssociation<A> {

    private final A from;
    private final A to;
    private final Class<A> type;

    public <F extends A, T extends A> DefaultBuildOutcomeAssociation(F from, T to, Class<A> type) {
        this.from = from;
        this.to = to;
        this.type = type;
    }

    public A getFrom() {
        return from;
    }

    public A getTo() {
        return to;
    }

    public Class<A> getType() {
        return type;
    }
}
