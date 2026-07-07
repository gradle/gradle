/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.internal.service;

import org.jspecify.annotations.Nullable;

import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.Collection;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * A {@link ServiceLookup} that filters services of another service lookup
 * to only permit a subset of services.
 */
public class FilteringServiceLookup implements ServiceLookup {

    private final Predicate<Class<?>> filter;
    private final FilterAction filterAction;
    private final ServiceLookup delegate;
    private final ServiceLookup userTypeFilteredView;

    public FilteringServiceLookup(
        ServiceLookup delegate,
        Collection<Class<?>> allowedClasses,
        FilterAction filterAction
    ) {
        this(delegate, type -> allowsService(type, allowedClasses), filterAction);
    }

    public FilteringServiceLookup(
        ServiceLookup delegate,
        Predicate<Class<?>> filter,
        FilterAction filterAction
    ) {
        if (!(filterAction instanceof FilterAction.Deny) && !(filterAction instanceof FilterAction.Permit)) {
            throw new IllegalArgumentException("FilterAction must implement either FilterAction.Deny or FilterAction.Permit");
        }
        this.filter = filter;
        this.delegate = delegate;
        this.filterAction = filterAction;

        ServiceLookup filteredDelegate = delegate.withUserTypeFilter();
        this.userTypeFilteredView = filteredDelegate == delegate
            ? this
            : new FilteringServiceLookup(filteredDelegate, filter, filterAction);
    }

    @Override
    public @Nullable Object find(Type serviceType) throws ServiceLookupException {
        if (passesFilter(serviceType)) {
            return delegate.find(serviceType);
        }

        if (filterAction instanceof FilterAction.Permit) {
            Object found = delegate.find(serviceType);
            if (found != null) {
                ((FilterAction.Permit) filterAction).onPermittedAccess(serviceType);
            }
            return found;
        }

        return null;
    }

    @Override
    public Object get(Type serviceType) throws UnknownServiceException, ServiceLookupException {
        if (passesFilter(serviceType)) {
            return delegate.get(serviceType);
        }

        if (filterAction instanceof FilterAction.Permit) {
            Object found = delegate.get(serviceType);
            ((FilterAction.Permit) filterAction).onPermittedAccess(serviceType);
            return found;
        }

        throw new UnknownServiceException(serviceType, ((FilterAction.Deny) filterAction).onDeniedAccess(serviceType));
    }

    @Override
    public Object get(Type serviceType, Class<? extends Annotation> annotatedWith) throws UnknownServiceException, ServiceLookupException {
        if (passesFilter(serviceType)) {
            return delegate.get(serviceType, annotatedWith);
        }

        if (filterAction instanceof FilterAction.Permit) {
            Object found = delegate.get(serviceType, annotatedWith);
            ((FilterAction.Permit) filterAction).onPermittedAccess(serviceType);
            return found;
        }

        throw new UnknownServiceException(serviceType, ((FilterAction.Deny) filterAction).onDeniedAccess(serviceType));
    }

    @Override
    public ServiceLookup withUserTypeFilter() {
        return userTypeFilteredView;
    }

    private boolean passesFilter(Type serviceType) {
        if (!(serviceType instanceof Class)) {
            return false;
        }

        Class<?> serviceClass = (Class<?>) serviceType;
        return filter.test(serviceClass);
    }

    /**
     * Determines how the filter handles services that do not pass the filter.
     * Implementations must extend either {@link Deny} or {@link Permit}.
     */
    public interface FilterAction {

        /**
         * Deny access to services which do not pass the filter. When {@link ServiceLookup#get} is
         * called for a filtered service, the exception is built via {@link #onDeniedAccess}.
         */
        interface Deny extends FilterAction {
            /**
             * Builds the exception message and performs any reporting for a denied service.
             * Called only when {@link FilteringServiceLookup#get} is about to throw.
             */
            String onDeniedAccess(Type serviceType);
        }

        /**
         * Permit access to services which do not pass the filter, with a side effect on access.
         * The side effect fires whenever a filtered-yet-permitted service is actually returned to
         * the caller (a deprecation warning, problem emission, etc.).
         */
        interface Permit extends FilterAction {
            /**
             * Hook invoked when a permitted service is actually returned from a lookup.
             * Used by {@link FilteringServiceLookup#find} only when the underlying delegate produced a non-null instance,
             * and by {@link FilteringServiceLookup#get} only when the underlying delegate returned successfully.
             */
            void onPermittedAccess(Type serviceType);
        }

        /**
         * Return a filter that denies access to services which do not pass the filter. The given
         * function is invoked only from the {@link FilteringServiceLookup#get} paths to build the exception message.
         */
        static Deny denyFilteredServices(Function<Type, String> onDeniedAccess) {
            return onDeniedAccess::apply;
        }

        /**
         * Return a filter that permits access to services which do not pass the filter. The given
         * consumer is invoked when a filtered-yet-permitted service is provided to the user
         * and may trigger a side effect like a deprecation warning or problem emission.
         */
        static Permit permitFilteredServices(Consumer<Type> onPermittedAccess) {
            return onPermittedAccess::accept;
        }
    }

    private static boolean allowsService(Class<?> serviceType, Collection<Class<?>> allowedServiceTypes) {
        for (Class<?> allowedService : allowedServiceTypes) {
            if (serviceType.isAssignableFrom(allowedService)) {
                return true;
            }
        }

        return false;
    }

}
