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

package org.gradle.api.internal

import org.gradle.api.Action
import org.gradle.api.DomainObjectCollection
import org.gradle.api.NamedDomainObjectList
import org.gradle.api.Transformer
import org.gradle.api.internal.provider.CollectionProviderInternal
import org.gradle.api.internal.provider.ProviderInternal
import org.gradle.api.internal.provider.Providers

abstract class DomainObjectCollectionConfigurationFactories {
    abstract static class AbstractConfigurationFactory<T, F> {
        private boolean isAlreadyCalled = false

        F create(DomainObjectCollection<T> container, T element) {
            if (Closure == configurationType) {
                return { AbstractConfigurationFactory.this.call(container, element) }
            }

            return new Action<T>() {
                @Override
                void execute(T t) {
                    AbstractConfigurationFactory.this.call(container, element)
                }
            }
        }

        def call(DomainObjectCollection<T> container, T element) {
            if (!isAlreadyCalled) {
                isAlreadyCalled = true
                doCall(container, element)
            }
        }

        abstract void doCall(DomainObjectCollection<T> container, T element)

        boolean isUseExternalProviders() {
            return false
        }
    }

    abstract static class CallAddFactory<T, F> extends AbstractConfigurationFactory<T, F> {
        @Override
        void doCall(DomainObjectCollection<T> container, T element) {
            container.add(element)
        }

        static class AsAction<T> extends CallAddFactory<T, Action<T>> {
            static def configurationType = Action
        }

        static class AsClosure<T> extends CallAddFactory<T, Closure> {
            static def configurationType = Closure
        }
    }

    abstract static class CallAddLaterFactory<T, F> extends AbstractConfigurationFactory<T, F> {
        boolean useExternalProviders = true

        @Override
        void doCall(DomainObjectCollection<T> container, T element) {
            container.addLater(Providers.of(element))
        }

        static class AsAction<T> extends CallAddLaterFactory<T, Action<T>> {
            static def configurationType = Action
        }

        static class AsClosure<T> extends CallAddLaterFactory<T, Closure> {
            static def configurationType = Closure
        }
    }

    abstract static class CallAddAllLaterFactory<T, F> extends AbstractConfigurationFactory<T, F> {
        boolean useExternalProviders = true

        @Override
        void doCall(DomainObjectCollection<T> container, T element) {
            container.addAllLater(new CollectionProviderInternal<T, Collection<T>>() {
                @Override
                Class getElementType() {
                    return type
                }

                @Override
                int size() {
                    return 1
                }

                @Override
                Class getType() {
                    return List
                }

                @Override
                ProviderInternal<T> map(Transformer transformer) {
                    throw new UnsupportedOperationException()
                }

                @Override
                Collection<T> get() {
                    return [element]
                }

                @Override
                Collection<T> getOrNull() {
                    return get()
                }

                @Override
                Collection<T> getOrElse(Collection<T> defaultValue) {
                    return get()
                }

                @Override
                boolean isPresent() {
                    return true
                }
            })
        }

        static class AsAction<T> extends CallAddAllLaterFactory<T, Action<T>> {
            static def configurationType = Action
        }

        static class AsClosure<T> extends CallAddAllLaterFactory<T, Closure> {
            static def configurationType = Closure
        }
    }

    abstract static class CallAddAllFactory<T, F> extends AbstractConfigurationFactory<T, F> {
        @Override
        void doCall(DomainObjectCollection<T> container, T element) {
            container.addAll([element])
        }

        static class AsAction<T> extends CallAddAllFactory<T, Action<T>> {
            static def configurationType = Action
        }

        static class AsClosure<T> extends CallAddAllFactory<T, Closure> {
            static def configurationType = Closure
        }
    }

    abstract static class CallClearFactory<T, F> extends AbstractConfigurationFactory<T, F> {
        @Override
        void doCall(DomainObjectCollection<T> container, T element) {
            container.clear()
        }

        static class AsAction<T> extends CallClearFactory<T, Action<T>> {
            static def configurationType = Action
        }

        static class AsClosure<T> extends CallClearFactory<T, Closure> {
            static def configurationType = Closure
        }
    }

    abstract static class CallRemoveFactory<T, F> extends AbstractConfigurationFactory<T, F> {
        @Override
        void doCall(DomainObjectCollection<T> container, T element) {
            container.remove(element)
        }

        static class AsAction<T> extends CallRemoveFactory<T, Action<T>> {
            static def configurationType = Action
        }

        static class AsClosure<T> extends CallRemoveFactory<T, Closure> {
            static def configurationType = Closure
        }
    }

    abstract static class CallRemoveAllFactory<T, F> extends AbstractConfigurationFactory<T, F> {
        @Override
        void doCall(DomainObjectCollection<T> container, T element) {
            container.removeAll([element])
        }

        static class AsAction<T> extends CallRemoveAllFactory<T, Action<T>> {
            static def configurationType = Action
        }

        static class AsClosure<T> extends CallRemoveAllFactory<T, Closure> {
            static def configurationType = Closure
        }
    }

    abstract static class CallRetainAllFactory<T, F> extends AbstractConfigurationFactory<T, F> {
        @Override
        void doCall(DomainObjectCollection<T> container, T element) {
            container.retainAll([element])
        }

        static class AsAction<T, F> extends CallRetainAllFactory<T, F> {
            static def configurationType = Action
        }

        static class AsClosure<T, F> extends CallRetainAllFactory<T, F> {
            static def configurationType = Closure
        }
    }

    abstract static class CallRemoveOnIteratorFactory<T, F> extends AbstractConfigurationFactory<T, F> {
        @Override
        void doCall(DomainObjectCollection<T> container, T element) {
            def iter = container.iterator()
            iter.next()
            iter.remove()
        }

        static class AsAction<T> extends CallRemoveOnIteratorFactory<T, Action<T>> {
            static def configurationType = Action
        }

        static class AsClosure<T> extends CallRemoveOnIteratorFactory<T, Closure> {
            static def configurationType = Closure
        }
    }

    abstract static class AbstractNamedConfigurationFactory<T, F> extends AbstractConfigurationFactory<T, F> {
        @Override
        void doCall(DomainObjectCollection<T> container, T element) {
            doCall((NamedDomainObjectList) container, element)
        }

        abstract void doCall(NamedDomainObjectList<T> container, T element)
    }

    abstract static class CallInsertFactory<T, F> extends AbstractNamedConfigurationFactory<T, F> {
        @Override
        void doCall(NamedDomainObjectList<T> container, T element) {
            container.add(0, element)
        }

        static class AsAction<T> extends CallInsertFactory<T, Action<T>> {
            static def configurationType = Action
        }

        static class AsClosure<T> extends CallInsertFactory<T, Closure> {
            static def configurationType = Closure
        }
    }

    abstract static class CallInsertAllFactory<T, F> extends AbstractNamedConfigurationFactory<T, F> {
        @Override
        void doCall(NamedDomainObjectList<T> container, T element) {
            container.addAll(0, [element])
        }

        static class AsAction<T> extends CallInsertAllFactory<T, Action<T>> {
            static def configurationType = Action
        }

        static class AsClosure<T> extends CallInsertAllFactory<T, Closure> {
            static def configurationType = Closure
        }
    }

    abstract static class CallSetFactory<T, F> extends AbstractNamedConfigurationFactory<T, F> {
        @Override
        void doCall(NamedDomainObjectList<T> container, T element) {
            container.set(0, element)
        }

        static class AsAction<T> extends CallSetFactory<T, Action<T>> {
            static def configurationType = Action
        }

        static class AsClosure<T> extends CallSetFactory<T, Closure> {
            static def configurationType = Closure
        }
    }

    abstract static class CallRemoveWithIndexFactory<T, F> extends AbstractNamedConfigurationFactory<T, F> {
        @Override
        void doCall(NamedDomainObjectList<T> container, T element) {
            container.remove(0)
        }

        static class AsAction<T> extends CallRemoveWithIndexFactory<T, Action<T>> {
            static def configurationType = Action
        }

        static class AsClosure<T> extends CallRemoveWithIndexFactory<T, Closure> {
            static def configurationType = Closure
        }
    }

    abstract static class CallAddOnListIteratorFactory<T, F> extends AbstractNamedConfigurationFactory<T, F> {
        @Override
        void doCall(NamedDomainObjectList<T> container, T element) {
            def iter = container.listIterator()
            iter.next()
            iter.add(element)
        }

        static class AsAction<T> extends CallAddOnListIteratorFactory<T, Action<T>> {
            static def configurationType = Action
        }

        static class AsClosure<T> extends CallAddOnListIteratorFactory<T, Closure> {
            static def configurationType = Closure
        }
    }

    abstract static class CallSetOnListIteratorFactory<T, F> extends AbstractNamedConfigurationFactory<T, F> {
        @Override
        void doCall(NamedDomainObjectList<T> container, T element) {
            def iter = container.listIterator()
            iter.next()
            iter.set(element)
        }

        static class AsAction<T> extends CallSetOnListIteratorFactory<T, Action<T>> {
            static def configurationType = Action
        }

        static class AsClosure<T> extends CallSetOnListIteratorFactory<T, Closure> {
            static def configurationType = Closure
        }
    }

    abstract static class CallRemoveOnListIteratorFactory<T, F> extends AbstractNamedConfigurationFactory<T, F> {
        @Override
        void doCall(NamedDomainObjectList<T> container, T element) {
            def iter = container.listIterator()
            iter.next()
            iter.remove()
        }

        static class AsAction<T> extends CallRemoveOnListIteratorFactory<T, Action<T>> {
            static def configurationType = Action
        }

        static class AsClosure<T> extends CallRemoveOnListIteratorFactory<T, Closure> {
            static def configurationType = Closure
        }
    }
}
