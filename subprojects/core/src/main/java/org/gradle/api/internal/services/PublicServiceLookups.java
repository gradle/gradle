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

package org.gradle.api.internal.services;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.file.ArchiveOperations;
import org.gradle.api.file.BuildLayout;
import org.gradle.api.file.FileSystemOperations;
import org.gradle.api.file.ProjectLayout;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.api.services.BuildService;
import org.gradle.internal.RenderingUtils;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.process.ExecOperations;
import org.jspecify.annotations.Nullable;

import java.util.Collection;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Validates and performs lookups of the services that Gradle exposes to scripts and plugins
 * via {@code Project.service(Class)}, {@code Task.service(Class)}, {@code Settings.service(Class)}
 * and {@code Gradle.service(Class)}.
 *
 * <p>Only the services in the explicit allowlist below can be obtained. Validation is decided from
 * the requested type alone; the backing {@link ServiceRegistry} is only consulted for allowed types
 * and is never exposed to the caller.
 */
public final class PublicServiceLookups {

    /**
     * The API entry point a lookup was made through, determining which services are available
     * and which scope's registry backs the lookup.
     */
    public enum EntryPoint {
        PROJECT("project scripts", "project plugins"),
        TASK("tasks"),
        SETTINGS("settings scripts", "settings plugins"),
        GRADLE("init scripts", "init plugins");

        private final ImmutableList<String> displayNames;

        EntryPoint(String... displayNames) {
            this.displayNames = ImmutableList.copyOf(displayNames);
        }

        public Collection<String> getDisplayNames() {
            return displayNames;
        }
    }

    private static final ImmutableSet<EntryPoint> ALL_ENTRY_POINTS = Sets.immutableEnumSet(EnumSet.allOf(EntryPoint.class));

    // Iteration order is meaningful: error messages enumerate the entries in this order.
    private static final ImmutableMap<Class<?>, ImmutableSet<EntryPoint>> AVAILABLE_SERVICES = ImmutableMap.<Class<?>, ImmutableSet<EntryPoint>>builder()
        .put(ObjectFactory.class, ALL_ENTRY_POINTS)
        .put(ProviderFactory.class, ALL_ENTRY_POINTS)
        .put(FileSystemOperations.class, ALL_ENTRY_POINTS)
        .put(ArchiveOperations.class, ALL_ENTRY_POINTS)
        .put(ExecOperations.class, Sets.immutableEnumSet(EntryPoint.TASK))
        .put(ProjectLayout.class, Sets.immutableEnumSet(EntryPoint.PROJECT, EntryPoint.TASK))
        .put(BuildLayout.class, Sets.immutableEnumSet(EntryPoint.SETTINGS))
        .build();

    private PublicServiceLookups() {
    }

    /**
     * Visible for testing: the authoritative service→scopes allowlist, so the marker/bound consistency
     * test can assert the compile-time markers and method bounds agree with it.
     */
    static ImmutableMap<Class<?>, ImmutableSet<EntryPoint>> availableServices() {
        return AVAILABLE_SERVICES;
    }

    /**
     * Resolves a public service, enforcing the curated allowlist at runtime.
     *
     * <p>The marker interfaces ({@code ProjectService}, {@code TaskService}, etc.) are only a compile-time
     * convenience, not a trust boundary: they are unsealed, so third-party code can implement one to satisfy
     * the bound, and Groovy/reflective calls skip it entirely. This method matches {@code serviceType} by
     * <strong>exact identity</strong> against {@link #AVAILABLE_SERVICES} (never {@code instanceof}) and only
     * then queries the {@link ServiceRegistry}. So a caller-supplied type that merely implements a marker is
     * rejected, and is never resolved or executed through Gradle's DI container.</p>
     *
     * @throws InvalidUserDataException if the type is null, not allowlisted, or not available in this scope
     */
    public static <T> T lookup(@Nullable Class<T> serviceType, EntryPoint entryPoint, ServiceRegistry services) {
        if (serviceType == null) {
            throw new InvalidUserDataException("The service type given to service() must not be null.");
        }
        // Identity check: a user type that only implements a marker is not a key here, so it never reaches the registry.
        Set<EntryPoint> availableIn = AVAILABLE_SERVICES.get(serviceType);
        if (availableIn == null) {
            throw new InvalidUserDataException(unknownServiceMessage(serviceType, entryPoint));
        }
        if (!availableIn.contains(entryPoint)) {
            throw new InvalidUserDataException(wrongScopeMessage(serviceType, entryPoint, availableIn));
        }
        return services.get(serviceType);
    }

    private static String unknownServiceMessage(Class<?> serviceType, EntryPoint entryPoint) {
        if (BuildService.class.isAssignableFrom(serviceType)) {
            return String.format(
                "%s is a shared build service, which cannot be obtained with service(). " +
                    "Register it with gradle.sharedServices.registerIfAbsent() and access it " +
                    "via a property annotated with @ServiceReference, or via the provider returned from registration.",
                serviceType.getName());
        }
        return String.format(
            "%s is not a service that is available for lookup with service(). The following services are available in %s: %s.",
            serviceType.getName(),
            entryPoint.getDisplayNames().stream().collect(RenderingUtils.oxfordJoin("and")),
            servicesAvailableIn(entryPoint));
    }

    private static String wrongScopeMessage(Class<?> serviceType, EntryPoint entryPoint, Set<EntryPoint> availableIn) {
        return String.format(
            "%s is not available in %s. It is available in %s.",
            serviceType.getName(), entryPoint.getDisplayNames().stream().collect(RenderingUtils.oxfordJoin("and")),
            availableIn.stream().flatMap(e -> e.getDisplayNames().stream()).collect(RenderingUtils.oxfordJoin("and")));
    }

    private static String servicesAvailableIn(EntryPoint entryPoint) {
        return AVAILABLE_SERVICES.entrySet().stream()
            .filter(entry -> entry.getValue().contains(entryPoint))
            .map(entry -> entry.getKey().getName())
            .collect(Collectors.joining(", "));
    }
}
