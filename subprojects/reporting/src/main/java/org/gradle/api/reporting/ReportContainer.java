/*
 * Copyright 2009 the original author or authors.
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

package org.gradle.api.reporting;

import org.gradle.api.GradleException;
import org.gradle.api.Incubating;
import org.gradle.api.NamedDomainObjectSet;
import org.gradle.api.Namer;
import org.gradle.api.Rule;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.OutputDirectories;
import org.gradle.api.tasks.OutputFiles;
import org.gradle.util.Configurable;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.SortedSet;

/**
 * A container of {@link Report} objects, that represent potential reports.
 * <p>
 * Things that produce reports (typically tasks) expose a report container that contains {@link Report} objects for each
 * possible report that they can produce. Each report object can be configured individually, including whether or not it should
 * be produced by way of its {@link Report#setEnabled(boolean) enabled} property.
 * <p>
 * {@code ReportContainer} implementations are <b>immutable</b> in that standard collection methods such as {@code add()}, {@code remove()}
 * and {@code clear()} will throw an {@link ImmutableViolationException}. However, implementations may provide new methods that allow
 * the addition of new report object and/or the removal of existing report objects.
 *
 * @param <T> The base report type for reports of this container.
 */
public interface ReportContainer<T extends Report> extends NamedDomainObjectSet<T>, Configurable<ReportContainer<T>> {

    /**
     * The exception thrown when any of this container's mutation methods are called.
     * <p>
     * This applies to the standard {@link java.util.Collection} methods such as {@code add()}, {@code remove()}
     * and {@code clear()}.
     */
    class ImmutableViolationException extends GradleException {
        public ImmutableViolationException() {
            super("ReportContainer objects are immutable");
        }
    }

    /**
     * Returns an immutable collection of all the enabled {@link Report} objects in this container.
     * <p>
     * The returned collection is live. That is, as reports are enabled/disabled the returned collection always
     * reflects the current set of enabled reports.
     *
     * @return The enabled reports.
     */
    @Internal
    NamedDomainObjectSet<T> getEnabled();

    @Override
    @Internal
    Namer<T> getNamer();

    @Override
    @Internal
    SortedMap<String, T> getAsMap();

    @Override
    @Internal
    SortedSet<String> getNames();

    @Override
    @Internal
    List<Rule> getRules();

    @Override
    @Internal
    boolean isEmpty();

    @Incubating
    @OutputDirectories
    Map<String, File> getEnabledDirectoryReportDestinations();

    @Incubating
    @OutputFiles
    Map<String, File> getEnabledFileReportDestinations();

    @Incubating
    @Input
    SortedSet<String> getEnabledReportNames();
}
