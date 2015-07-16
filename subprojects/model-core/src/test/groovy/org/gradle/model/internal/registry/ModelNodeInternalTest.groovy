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

package org.gradle.model.internal.registry

import org.gradle.model.RuleSource
import org.gradle.model.internal.core.*
import org.gradle.model.internal.core.rule.describe.ModelRuleDescriptor
import org.gradle.model.internal.type.ModelType
import spock.lang.Specification
import spock.lang.Unroll

class ModelNodeInternalTest extends Specification {
    CreatorRuleBinder creatorRuleBinder = Mock()

    def "should have zero executed rules initially"() {
        expect:
        new ModelNodeInternalImpl(creatorRuleBinder).getExecutedRules().size() == 0
    }

    @Unroll
    def "should record executed rules when notify fired #fireCount time(s)"() {
        ModelRuleDescriptor descriptor = Mock()
        ModelNodeInternal modelNode = new ModelNodeInternalImpl(creatorRuleBinder)
        MutatorRuleBinder executionBinder = Mock()
        executionBinder.isBound() >> true
        executionBinder.getInputBindings() >> []
        executionBinder.getDescriptor() >> descriptor

        when:
        fireCount.times {
            modelNode.notifyFired(executionBinder)
        }

        then:
        modelNode.executedRules.size() == fireCount
        modelNode.executedRules[0] == descriptor

        where:
        fireCount << [1, 2]
    }

    def "should not fire for unbound binders"() {
        setup:
        ModelNodeInternal modelNode = new ModelNodeInternalImpl(creatorRuleBinder)
        MutatorRuleBinder executionBinder = Mock()
        executionBinder.isBound() >> false

        when:
        modelNode.notifyFired(executionBinder)

        then:
        AssertionError e = thrown()
        e.message == 'RuleBinder must be in a bound state'
    }


    class ModelNodeInternalImpl extends ModelNodeInternal {

        ModelNodeInternalImpl(CreatorRuleBinder creatorBinder) {
            super(creatorBinder)
        }

        @Override
        void addReference(ModelCreator creator) {

        }

        @Override
        void addLink(ModelCreator creator) {

        }

        @Override
        void removeLink(String name) {

        }

        @Override
        def <T> void applyToSelf(ModelActionRole type, ModelAction<T> action) {

        }

        @Override
        def <T> void applyToAllLinks(ModelActionRole type, ModelAction<T> action) {

        }

        @Override
        def <T> void applyToAllLinksTransitive(ModelActionRole type, ModelAction<T> action) {

        }

        @Override
        def <T> void applyToLink(ModelActionRole type, ModelAction<T> action) {

        }

        @Override
        void applyToLink(String name, Class<? extends RuleSource> rules) {

        }

        @Override
        void applyToSelf(Class<? extends RuleSource> rules) {

        }

        @Override
        void applyToLinks(ModelType<?> type, Class<? extends RuleSource> rules) {

        }

        @Override
        void applyToAllLinksTransitive(ModelType<?> type, Class<? extends RuleSource> rules) {

        }

        @Override
        MutableModelNode getLink(String name) {
            return null
        }

        @Override
        int getLinkCount(ModelType<?> type) {
            return 0
        }

        @Override
        boolean hasLink(String name) {
            return false
        }

        @Override
        boolean hasLink(String name, ModelType<?> type) {
            return false
        }

        @Override
        Set<String> getLinkNames(ModelType<?> type) {
            return null
        }

        @Override
        Iterable<? extends MutableModelNode> getLinks(ModelType<?> type) {
            return null
        }

        @Override
        int getLinkCount() {
            return 0
        }

        @Override
        def <T> void setPrivateData(Class<? super T> type, T object) {

        }

        @Override
        def <T> void setPrivateData(ModelType<? super T> type, T object) {

        }

        @Override
        def <T> T getPrivateData(Class<T> type) {
            return null
        }

        @Override
        def <T> T getPrivateData(ModelType<T> type) {
            return null
        }

        @Override
        Object getPrivateData() {
            return null
        }

        @Override
        ModelNodeInternal getTarget() {
            return null
        }

        @Override
        void setTarget(ModelNode target) {

        }

        @Override
        void ensureUsable() {

        }

        @Override
        void realize() {

        }

        @Override
        MutableModelNode getParent() {
            return null
        }

        @Override
        Iterable<? extends ModelNodeInternal> getLinks() {
            return null
        }

        @Override
        ModelNodeInternal addLink(ModelNodeInternal node) {
            return null
        }
    }
}
