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

package org.gradle.model.internal.inspect

import org.gradle.model.Model
import org.gradle.model.Mutate
import org.gradle.model.Path
import org.gradle.model.RuleSource
import org.gradle.model.internal.fixture.ModelRegistryHelper
import org.gradle.model.internal.fixture.ProjectRegistrySpec

class MutationRuleExecutionOrderTest extends ProjectRegistrySpec {
    def modelRegistry = new ModelRegistryHelper(modelRuleExtractor)

    static class MutationRecorder {
        def mutations = []
    }

    static class ByPathRules extends RuleSource {
        @Model
        MutationRecorder recorder() {
            new MutationRecorder()
        }

        @Mutate
        void b(@Path("recorder") MutationRecorder recorder) {
            recorder.mutations << "b"
        }

        @Mutate
        void a(@Path("recorder") MutationRecorder recorder) {
            recorder.mutations << "a"
        }
    }

    def "mutation rules from the same plugin are applied in the order specified by their signatures"() {
        when:
        modelRegistry.getRoot().applyToSelf(ByPathRules)

        then:
        modelRegistry.get("recorder", MutationRecorder).mutations == ["a", "b"]
    }

    static class MixedRules extends RuleSource {
        @Model
        MutationRecorder recorder() {
            new MutationRecorder()
        }

        @Mutate
        void b(@Path("recorder") MutationRecorder recorder) {
            recorder.mutations << "b"
        }

        @Mutate
        void a(MutationRecorder recorder) {
            recorder.mutations << "a"
        }
    }

    def "mutation rule application order is consistent if by type subject bound rules are used"() {
        when:
        modelRegistry.getRoot().applyToSelf(MixedRules)

        then:
        modelRegistry.get("recorder", MutationRecorder).mutations == ["b", "a"]
    }

    static class MutationRulesWithInputs extends RuleSource {
        @Model
        MutationRecorder recorder() {
            new MutationRecorder()
        }

        @Mutate
        void b(MutationRecorder recorder, @Path("secondInput") String input) {
            recorder.mutations << input
        }

        @Mutate
        void a(MutationRecorder recorder, @Path("firstInput") String input) {
            recorder.mutations << input
        }
    }

    static class FirstInputCreationRule extends RuleSource {
        @Model
        String firstInput() {
            "first"
        }
    }

    static class SecondInputCreationRule extends RuleSource {
        @Model
        String secondInput() {
            "second"
        }
    }

    def "binding order does not affect mutation rule execution order"() {
        when:
        modelRegistry.getRoot().applyToSelf(MutationRulesWithInputs)
        modelRegistry.getRoot().applyToSelf(SecondInputCreationRule)
        modelRegistry.getRoot().applyToSelf(FirstInputCreationRule)

        then:
        modelRegistry.get("recorder", MutationRecorder).mutations == ["first", "second"]
    }
}
