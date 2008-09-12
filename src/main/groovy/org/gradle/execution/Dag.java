/*******************************************************************************
 * Copyright (c) 2006 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
/*
 * For the additions made to this class:
 * 
 * Copyright 2007 the original author or authors.
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

package org.gradle.execution;

import org.gradle.api.CircularReferenceException;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.internal.DefaultTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;


/**
 * A directed acyclic graph. See http://en.wikipedia.org/wiki/Directed_acyclic_graph
 */
public class Dag {
    private static final Logger logger = LoggerFactory.getLogger(Dag.class);

    /**
     * Multimap, supports <code>null</code> key, but not <code>null</code> values.
     */
    private static final class MultiMap {
        private final Map fMap = new LinkedHashMap();

        /**
         * Adds <code>val</code> to the values mapped to by <code>key</code>. If
         * <code>val</code> is <code>null</code>, <code>key</code> is added to the key set of
         * the multimap.
         *
         * @param key the key
         * @param val the value
         */
        public void put(Object key, Object val) {
            Set values = (Set) fMap.get(key);
            if (values == null) {
                values = new LinkedHashSet();
                fMap.put(key, values);
            }
            if (val != null)
                values.add(val);
        }

        /**
         * Returns all mappings for the given key, an empty set if there are no mappings.
         *
         * @param key the key
         * @return the mappings for <code>key</code>
         */
        public Set get(Object key) {
            Set values = (Set) fMap.get(key);
            return values == null ? Collections.EMPTY_SET : values;
        }

        public Set keySet() {
            return fMap.keySet();
        }

        /**
         * Removes all mappings for <code>key</code> and removes <code>key</code> from the key
         * set.
         *
         * @param key the key to remove
         * @return the removed mappings
         */
        public Set removeAll(Object key) {
            Set values = (Set) fMap.remove(key);
            return values == null ? Collections.EMPTY_SET : values;
        }

        /**
         * Removes a mapping from the multimap, but does not remove the <code>key</code> from the
         * key set.
         *
         * @param key the key
         * @param val the value
         */
        public void remove(Object key, Object val) {
            Set values = (Set) fMap.get(key);
            if (values != null)
                values.remove(val);
        }

        /*
           * @see java.lang.Object#toString()
           */
        public String toString() {
            return fMap.toString();
        }
    }

    private final MultiMap fOut = new MultiMap();
    private final MultiMap fIn = new MultiMap();

    /**
     * Adds a directed edge from <code>origin</code> to <code>target</code>. The vertices are not
     * required to exist prior to this call - if they are not currently contained by the graph, they are
     * automatically added.
     *
     * @param origin the origin vertex of the dependency
     * @param target the target vertex of the dependency
     * @return <code>true</code> if the edge was added, <code>false</code> if the
     *         edge was not added because it would have violated the acyclic nature of the
     *         receiver.
     */
    public boolean addEdge(Object origin, Object target) {
        assert origin != null;
        assert target != null;

        if (hasPath(target, origin))
            return false;

        fOut.put(origin, target);
        fOut.put(target, null);
        fIn.put(target, origin);
        fIn.put(origin, null);
        return true;
    }

    /**
     * Adds a vertex to the graph. If the vertex does not exist prior to this call, it is added with
     * no incoming or outgoing edges. Nothing happens if the vertex already exists.
     *
     * @param vertex the new vertex
     */
    public void addVertex(Object vertex) {
        assert vertex != null;
        fOut.put(vertex, null);
        fIn.put(vertex, null);
    }

    /**
     * Removes a vertex and all its edges from the graph.
     *
     * @param vertex the vertex to remove
     */
    public void removeVertex(Object vertex) {
        Set targets = fOut.removeAll(vertex);
        for (Iterator it = targets.iterator(); it.hasNext();)
            fIn.remove(it.next(), vertex);
        Set origins = fIn.removeAll(vertex);
        for (Iterator it = origins.iterator(); it.hasNext();)
            fOut.remove(it.next(), vertex);
    }

    /**
     * Returns the sources of the receiver. A source is a vertex with no incoming edges. The
     * returned set's iterator traverses the nodes in the order they were added to the graph.
     *
     * @return the sources of the receiver
     */
    public Set getSources() {
        return computeZeroEdgeVertices(fIn);
    }

    /**
     * Returns the sinks of the receiver. A sink is a vertex with no outgoing edges. The returned
     * set's iterator traverses the nodes in the order they were added to the graph.
     *
     * @return the sinks of the receiver
     */
    public Set getSinks() {
        return computeZeroEdgeVertices(fOut);
    }

    private Set computeZeroEdgeVertices(MultiMap map) {
        Set candidates = map.keySet();
        Set roots = new LinkedHashSet(candidates.size());
        for (Iterator it = candidates.iterator(); it.hasNext();) {
            Object candidate = it.next();
            if (map.get(candidate).isEmpty())
                roots.add(candidate);
        }
        return roots;
    }

    /**
     * Returns the direct children of a vertex. The returned {@link Set} is unmodifiable.
     *
     * @param vertex the parent vertex
     * @return the direct children of <code>vertex</code>
     */
    public Set getChildren(Object vertex) {
        return Collections.unmodifiableSet(fOut.get(vertex));
    }

    private boolean hasPath(Object start, Object end) {
        // break condition
        if (start == end)
            return true;

        Set children = fOut.get(start);
        for (Iterator it = children.iterator(); it.hasNext();)
            // recursion
            if (hasPath(it.next(), end))
                return true;
        return false;
    }

    /*
      * @see java.lang.Object#toString()
      */
    public String toString() {
        return "Out: " + fOut.toString() + " In: " + fIn.toString(); //$NON-NLS-1$ //$NON-NLS-2$
    }

    public void addTask(Task task, Iterable<? extends Task> dependsOnTasks) {
        logger.debug("Add task: {} DependsOnTasks: {}", task, dependsOnTasks);
        addVertex(task);
        for (Task dependsOnTask : dependsOnTasks) {
            if (!addEdge(task, dependsOnTask)) {
                throw new CircularReferenceException(String.format("Can't establish dependency %s ==> %s", task, dependsOnTask));
            }
        }
    }

    public boolean execute() {
        return execute(new TreeSet(getSources()));
    }

    private boolean execute(Set<DefaultTask> tasks) {
        boolean dagNeutral = true;
        for (DefaultTask task : tasks) {
            dagNeutral = execute(new TreeSet(getChildren(task)));
            if (!task.getExecuted()) {
                logger.info("Executing: " + task);
                task.execute();
                if (dagNeutral) {
                    dagNeutral = task.isDagNeutral();
                }
            }
        }
        return dagNeutral;
    }

    public boolean hasTask(String path) {
        assert path != null && path.length() > 0;
        for (DefaultTask task : getAllTasks()) {
            if (task.getPath().equals(path)) {
                return true;
            }
        }
        return false;
    }

    public Set<DefaultTask> getAllTasks() {
        return accumulateTasks(getSources());
    }

    private Set<DefaultTask> accumulateTasks(Set<DefaultTask> tasks) {
        Set<DefaultTask> resultTasks = new HashSet<DefaultTask>();
        for (DefaultTask task : tasks) {
            resultTasks.addAll(accumulateTasks(new HashSet(getChildren(task))));
            resultTasks.add(task);
        }
        return resultTasks;
    }

    public Set<Project> getProjects() {
        HashSet<Project> projects = new HashSet<Project>();
        for (DefaultTask task : getAllTasks()) {
            projects.add(task.getProject());
        }
        return projects;
    }

    public void reset() {
        fOut.fMap.clear();
        fIn.fMap.clear();
    }
}