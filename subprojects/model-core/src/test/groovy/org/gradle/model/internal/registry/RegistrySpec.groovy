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

import com.google.common.collect.ImmutableMultimap
import org.gradle.api.Nullable
import org.gradle.model.RuleSource
import org.gradle.model.internal.core.*
import org.gradle.model.internal.core.rule.describe.ModelRuleDescriptor
import org.gradle.model.internal.core.rule.describe.SimpleModelRuleDescriptor
import org.gradle.model.internal.type.ModelType
import spock.lang.Specification

class RegistrySpec extends Specification {
    protected static class TestNode extends ModelNodeInternal {
        def links = []

        TestNode(String creationPath, Class<?> type) {
            super(toBinder(creationPath, type))
        }

        TestNode(RegistrationRuleBinder creatorBinder) {
            super(creatorBinder)
        }

        private static RegistrationRuleBinder toBinder(String creationPath, Class<?> type) {
            def registration = ModelRegistrations.of(ModelPath.path(creationPath)).descriptor("test").withProjection(new UnmanagedModelProjection(ModelType.of(type))).build()
            def subject = new BindingPredicate()
            def binder = new RegistrationRuleBinder(registration, subject, [], [])
            binder
        }

        @Override
        Iterable<? extends ModelNodeInternal> getLinks() {
            return links
        }

        ModelNodeInternal addLink(ModelNodeInternal node) {
            links << node
            return node
        }

        @Override
        def <T> ModelView<? extends T> asMutable(ModelType<T> type, ModelRuleDescriptor ruleDescriptor, List<ModelView<?>> implicitDependencies) {
            return null
        }

        @Override
        void addReference(ModelRegistration registration) {

        }


        @Override
        void addLink(ModelRegistration registration) {

        }

        @Override
        void removeLink(String name) {

        }

        @Override
        def void applyToSelf(ModelActionRole type, ModelAction action) {

        }

        @Override
        def void applyToAllLinks(ModelActionRole type, ModelAction action) {

        }

        @Override
        def void applyToAllLinksTransitive(ModelActionRole type, ModelAction action) {

        }

        @Override
        def void applyToLink(ModelActionRole type, ModelAction action) {

        }

        @Override
        void applyToLinks(ModelType<?> type, Class<? extends RuleSource> rules) {

        }

        @Override
        void applyToAllLinksTransitive(ModelType<?> type, Class<? extends RuleSource> rules) {

        }

        @Override
        void applyToLink(String name, Class<? extends RuleSource> rules) {

        }

        @Override
        void applyToSelf(Class<? extends RuleSource> rules) {

        }

        @Override
        ModelNodeInternal getLink(String name) {
            return null
        }

        @Override
        int getLinkCount(ModelType<?> type) {
            return 0
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
        boolean hasLink(String name) {
            return false
        }

        @Override
        boolean hasLink(String name, ModelType<?> type) {
            return false
        }

        @Override
        def <T> void setPrivateData(ModelType<? super T> type, T object) {

        }

        @Override
        def <T> void setPrivateData(Class<? super T> type, T object) {

        }

        @Override
        protected void resetPrivateData() {

        }

        @Override
        def <T> T getPrivateData(ModelType<T> type) {
            return null
        }

        @Override
        def <T> T getPrivateData(Class<T> type) {
            return null
        }

        @Override
        Object getPrivateData() {
            return null
        }

        @Override
        void setTarget(ModelNode target) {

        }

        @Override
        void ensureUsable() {

        }

        @Override
        void ensureAtLeast(ModelNode.State state) {

        }

        @Override
        def <T> ModelView<? extends T> asImmutable(ModelType<T> type, @Nullable ModelRuleDescriptor ruleDescriptor) {
            return null
        }

        @Override
        MutableModelNode getParent() {
            return null
        }

        @Override
        void addProjection(ModelProjection projection) {
        }
    }

    protected static class RuleBinderTestBuilder {

        private ModelRuleDescriptor descriptor
        private BindingPredicate subjectReference
        private String subjectReferenceBindingPath
        private List<BindingPredicate> inputReferences = []
        private Map<Integer, String> boundInputReferencePaths = [:]

        void subjectReference(ModelReference<?> reference) {
            subjectReference = new BindingPredicate(reference)
        }

        void subjectReference(Class type) {
            subjectReference(ModelReference.of(ModelType.of(type)))
        }

        void subjectReference(String path, Class type) {
            subjectReference(ModelReference.of(new ModelPath(path), ModelType.of(type)))
        }

        void bindSubjectReference(String path) {
            subjectReferenceBindingPath = path
        }

        void inputReference(ModelReference<?> reference) {
            inputReferences.add(new BindingPredicate(reference))
        }

        void inputReference(Class type) {
            inputReference(ModelReference.of(ModelType.of(type)).inScope(ModelPath.ROOT))
        }

        void inputReference(Class type, ModelNode.State state) {
            inputReference(ModelReference.of(null, ModelType.of(type), state).inScope(ModelPath.ROOT))
        }

        void inputReference(String path, ModelNode.State state) {
            inputReference(ModelReference.of(ModelPath.path(path), ModelType.untyped(), state))
        }

        void inputReference(String path, Class type) {
            inputReference(ModelReference.of(new ModelPath(path), ModelType.of(type)))
        }

        void bindInputReference(int index, String path) {
            boundInputReferencePaths[index] = path
        }

        void descriptor(String descriptor) {
            this.descriptor = new SimpleModelRuleDescriptor(descriptor)
        }

        RuleBinder build() {
            def binder
            if (subjectReference == null) {
                def action = new ProjectionBackedModelRegistration(null, descriptor, false, false, false, [], ImmutableMultimap.of())
                binder = new RegistrationRuleBinder(action, new BindingPredicate(), inputReferences, [])
            } else {
                def action = NoInputsModelAction.of(subjectReference.reference, descriptor, {})
                binder = new ModelActionBinder(subjectReference, inputReferences, action, [])
            }
            if (subjectReferenceBindingPath) {
                binder.subjectBinding.boundTo = new TestNode(subjectReferenceBindingPath, Object)
            }
            boundInputReferencePaths.each { index, path ->
                binder.inputBindings[index].boundTo = new TestNode(path, Object)
            }
            return binder
        }
    }
}
