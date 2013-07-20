/*
 * Copyright 2013 the original author or authors.
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


package org.gradle.api.internal.artifacts.ivyservice.resolveengine.oldresult;

import org.gradle.api.internal.artifacts.DefaultResolvedDependency;
import org.gradle.api.internal.artifacts.ResolvedConfigurationIdentifier;

import java.util.LinkedList;
import java.util.List;

import static com.google.common.collect.Sets.newHashSet;

/**
 * By Szczepan Faber on 7/20/13
 */
class TransientResultsStore {
    private List<ResolutionResultEvent> events = new LinkedList<ResolutionResultEvent>();
    private final Object lock = new Object();
    private DefaultTransientConfigurationResults cache;

    public void resolvedDependency(ResolvedConfigurationIdentifier id) {
        events.add(new NewResolvedDependency(id));
    }

    public void done(ResolvedConfigurationIdentifier id) {
        events.add(new ResolutionDone(id));
    }

    public void firstLevelDependency(ResolvedConfigurationIdentifier id) {
        events.add(new FirstLevelDependency(id));
    }

    public void parentChildMapping(ResolvedConfigurationIdentifier parent, ResolvedConfigurationIdentifier child) {
        events.add(new ParentChildMapping(parent, child));
    }

    public void parentSpecificArtifact(ResolvedConfigurationIdentifier child, ResolvedConfigurationIdentifier parent, long artifactId) {
        events.add(new ParentSpecificArtifact(child, parent, artifactId));
    }

    public TransientConfigurationResults load(ResolvedContentsMapping mapping) {
        synchronized (lock) {
            if (cache != null) {
                return cache;
            }
            cache = new DefaultTransientConfigurationResults();
            for (ResolutionResultEvent e : events) {
                if (e instanceof NewResolvedDependency) {
                    NewResolvedDependency ev = (NewResolvedDependency) e;
                    cache.allDependencies.put(ev.id, new DefaultResolvedDependency(ev.id.getId(), ev.id.getConfiguration()));
                } else if (e instanceof ParentChildMapping) {
                    ParentChildMapping ev = (ParentChildMapping) e;
                    DefaultResolvedDependency parent = cache.allDependencies.get(ev.parent);
                    DefaultResolvedDependency child = cache.allDependencies.get(ev.child);
                    parent.addChild(child);
                } else if (e instanceof FirstLevelDependency) {
                    FirstLevelDependency ev = (FirstLevelDependency) e;
                    cache.firstLevelDependencies.put(mapping.getModuleDependency(ev.id), cache.allDependencies.get(ev.id));
                } else if (e instanceof ResolutionDone) {
                    ResolutionDone ev = (ResolutionDone) e;
                    cache.root = cache.allDependencies.get(ev.id);
                } else if (e instanceof ParentSpecificArtifact) {
                    ParentSpecificArtifact ev = (ParentSpecificArtifact) e;
                    DefaultResolvedDependency child = cache.allDependencies.get(ev.child);
                    DefaultResolvedDependency parent = cache.allDependencies.get(ev.parent);
                    child.addParentSpecificArtifacts(parent, newHashSet(mapping.getArtifact(ev.artifactId)));
                } else {
                    throw new IllegalStateException("Unknown resolution event: " + e);
                }
            }
            return cache;
        }
    }

    private static interface ResolutionResultEvent {}

    private static class NewResolvedDependency implements ResolutionResultEvent {
        private ResolvedConfigurationIdentifier id;
        public NewResolvedDependency(ResolvedConfigurationIdentifier id) {
            this.id = id;
        }
    }

    private static class ResolutionDone implements ResolutionResultEvent {
        private ResolvedConfigurationIdentifier id;
        public ResolutionDone(ResolvedConfigurationIdentifier id) {
            this.id = id;
        }
    }

    private static class FirstLevelDependency implements ResolutionResultEvent {
        private ResolvedConfigurationIdentifier id;
        public FirstLevelDependency(ResolvedConfigurationIdentifier id) {
            this.id = id;
        }
    }

    private static class ParentChildMapping implements ResolutionResultEvent {
        private ResolvedConfigurationIdentifier parent;
        private ResolvedConfigurationIdentifier child;

        public ParentChildMapping(ResolvedConfigurationIdentifier parent, ResolvedConfigurationIdentifier child) {
            this.parent = parent;
            this.child = child;
        }
    }

    private static class ParentSpecificArtifact implements ResolutionResultEvent {
        private ResolvedConfigurationIdentifier child;
        private ResolvedConfigurationIdentifier parent;
        private long artifactId;

        public ParentSpecificArtifact(ResolvedConfigurationIdentifier child, ResolvedConfigurationIdentifier parent, long artifactId) {
            this.child = child;
            this.parent = parent;
            this.artifactId = artifactId;
        }
    }
}
