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

package org.gradle.integtests.fixtures;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import org.gradle.api.specs.Spec;
import org.gradle.util.CollectionUtils;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static com.google.common.collect.Iterators.getLast;

public abstract class AbstractContextualMultiVersionSpecRunner<T extends AbstractContextualMultiVersionSpecRunner.VersionedTool> extends AbstractMultiTestRunner {
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
        Set<T> partialVersions = Sets.newHashSet();
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
        Set<T> allAvailable = Sets.newHashSet();
        CollectionUtils.filter(getAllVersions(), allAvailable, new Spec<T>() {
            @Override
            public boolean isSatisfiedBy(T version) {
                return isAvailable(version);
            }
        });
        return allAvailable;
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

    public AbstractContextualMultiVersionSpecRunner(Class<?> target) {
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
        Set<T> versionsUnderTest = Sets.newHashSet();
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
        Set<T> versionsUnderTest = Sets.newHashSet();

        for (String criteria : selectionCriteria) {
            if ("latest".equals(criteria)) {
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

    protected enum CoverageContext {
        DEFAULT("default"), LATEST("latest"), PARTIAL("partial"), FULL("all"), UNKNOWN(null);

        final String selector;

        CoverageContext(String selector) {
            this.selector = selector;
        }

        static CoverageContext from(String requested) {
            for (CoverageContext context : values()) {
                if (context != UNKNOWN && context.selector.equals(requested)) {
                    return context;
                }
            }
            return UNKNOWN;
        }
    }

    public interface VersionedTool {
        boolean matches(String criteria);
    }
}
