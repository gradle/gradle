/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.integtests.fixtures.compatibility;

import com.google.common.collect.Lists;
import org.gradle.integtests.fixtures.VersionedTool;
import org.gradle.integtests.fixtures.extensions.AbstractMultiTestInterceptor;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.google.common.collect.Iterators.getLast;

/**
 * Tests using this runner and its subtypes will run by default the first version specified.
 * <p>
 * The following command line flag is used to determine the versions to run:
 * <ul>
 *     <li>{@code -PtestVersions=(default|all|partial|1,2,3)} will run the tests according to the configured value
 *     <ul>
 *         <li>{@code default} will run only the first configured version</li>
 *         <li>{@code all} will run all configured versions</li>
 *         <li>{@code partial} will run the first and last configured versions</li>
 *         <li>{@code 1,2,3} will run with versions {@code 1}, {@code 2} and {@code 3}</li>
 *     </ul>
 *     </li>
 * </ul>
 */
public abstract class AbstractContextualMultiVersionTestInterceptor<T extends VersionedTool> extends AbstractMultiTestInterceptor {
    public static final String VERSIONS_SYSPROP_NAME = "org.gradle.integtest.versions";

    protected abstract Collection<T> getAllVersions();

    protected Collection<T> getQuickVersions() {
        for (T next : getAllVersions()) {
            if (isAvailable(next)) {
                return Collections.singleton(next);
            }
        }
        return Collections.emptyList();
    }

    protected Collection<T> getPartialVersions() {
        Collection<T> allVersions = getAllVersions();
        Set<T> partialVersions = new HashSet<>();
        T firstAvailable = getFirstAvailable(allVersions);
        if (firstAvailable != null) {
            partialVersions.add(firstAvailable);
        }
        T lastAvailable = getLastAvailable(allVersions);
        if (lastAvailable != null) {
            partialVersions.add(lastAvailable);
        }
        return partialVersions;
    }

    private Collection<T> getAvailableVersions() {
        return getAllVersions().stream().filter(this::isAvailable).collect(Collectors.toSet());
    }

    private T getFirstAvailable(Collection<T> versions) {
        for (T next : versions) {
            if (isAvailable(next)) {
                return next;
            }
        }
        return null;
    }

    private T getLastAvailable(Collection<T> versions) {
        T lastAvailable = null;

        for (T next : versions) {
            if (isAvailable(next)) {
                lastAvailable = next;
            }
        }

        return lastAvailable;
    }

    protected abstract boolean isAvailable(T version);

    protected abstract Collection<Execution> createExecutionsFor(T versionedTool);

    public AbstractContextualMultiVersionTestInterceptor(Class<?> target) {
        super(target);
    }

    @Override
    protected void createExecutions() {
        String versions = System.getProperty(VERSIONS_SYSPROP_NAME, CoverageContext.DEFAULT.selector);
        CoverageContext coverageContext = CoverageContext.from(versions);
        if (coverageContext == CoverageContext.UNKNOWN) {
            List<String> selectionCriteria = Lists.newArrayList(versions.split(","));
            createSelectedExecutions(selectionCriteria);
        } else {
            createExecutionsForContext(coverageContext);
        }
    }

    protected void createExecutionsForContext(CoverageContext coverageContext) {
        Set<T> versionsUnderTest = new HashSet<>();
        switch(coverageContext) {
            case DEFAULT:
            case LATEST:
                versionsUnderTest.addAll(getQuickVersions());
                break;
            case PARTIAL:
                versionsUnderTest.addAll(getPartialVersions());
                break;
            case FULL:
                versionsUnderTest.addAll(getAvailableVersions());
                break;
            default:
                throw new IllegalArgumentException();
        }

        for (T version : versionsUnderTest) {
            for (Execution execution : createExecutionsFor(version)) {
                add(execution);
            }
        }
    }

    private void createSelectedExecutions(List<String> selectionCriteria) {
        Collection<T> possibleVersions = getAllVersions();
        Set<T> versionsUnderTest = new HashSet<>();

        for (String criteria : selectionCriteria) {
            if (CoverageContext.LATEST.selector.equals(criteria)) {
                versionsUnderTest.add(getLast(possibleVersions.iterator()));
            } else {
                for (T version : possibleVersions) {
                    if (isAvailable(version) && version.matches(criteria)) {
                        versionsUnderTest.add(version);
                    }
                }
            }
        }

        for (T version : versionsUnderTest) {
            for (Execution execution : createExecutionsFor(version)) {
                add(execution);
            }
        }
    }
}
