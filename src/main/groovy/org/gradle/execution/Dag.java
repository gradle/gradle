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

import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;


/**
 * A directed acyclic graph. See http://en.wikipedia.org/wiki/Directed_acyclic_graph
 */
public class Dag<T> {

    /**
     * Multimap, supports <code>null</code> key, but not <code>null</code> values.
     */
    private static final class MultiMap<T> {
        private final Map<T, Set<T>> fMap = new LinkedHashMap<T, Set<T>>();

        /**
         * Adds <code>val</code> to the values mapped to by <code>key</code>. If
         * <code>val</code> is <code>null</code>, <code>key</code> is added to the key set of
         * the multimap.
         *
         * @param key the key
         * @param val the value
         */
        public void put(T key, T val) {
            Set<T> values = fMap.get(key);
            if (values == null) {
                values = new LinkedHashSet<T>();
                fMap.put(key, values);
            }
            if (val != null) {
                values.add(val);
            }
        }

        /**
         * Returns all mappings for the given key, an empty set if there are no mappings.
         *
         * @param key the key
         * @return the mappings for <code>key</code>
         */
        public Set<T> get(T key) {
            Set<T> values =  fMap.get(key);
            return values == null ? Collections.<T>emptySet() : values;
        }

        public Set<T> keySet() {
            return fMap.keySet();
        }

        /**
         * Removes all mappings for <code>key</code> and removes <code>key</code> from the key
         * set.
         *
         * @param key the key to remove
         * @return the removed mappings
         */
        public Set<T> removeAll(T key) {
            Set<T> values = fMap.remove(key);
            return values == null ? Collections.<T>emptySet() : values;
        }

        /**
         * Removes a mapping from the multimap, but does not remove the <code>key</code> from the
         * key set.
         *
         * @param key the key
         * @param val the value
         */
        public void remove(T key, T val) {
            Set<T> values = fMap.get(key);
            if (values != null) {
                values.remove(val);
            }
        }

        /*
           * @see java.lang.Object#toString()
           */
        public String toString() {
            return fMap.toString();
        }
    }

    private final MultiMap<T> fOut = new MultiMap<T>();
    private final MultiMap<T> fIn = new MultiMap<T>();

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
    public boolean addEdge(T origin, T target) {
        assert origin != null;
        assert target != null;

        if (hasPath(target, origin)) {
            return false;
        }

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
    public void addVertex(T vertex) {
        assert vertex != null;
        fOut.put(vertex, null);
        fIn.put(vertex, null);
    }

    /**
     * Removes a vertex and all its edges from the graph.
     *
     * @param vertex the vertex to remove
     */
    public void removeVertex(T vertex) {
        Set<T> targets = fOut.removeAll(vertex);
        for (T target : targets) {
            fIn.remove(target, vertex);
        }
        Set<T> origins = fIn.removeAll(vertex);
        for (T origin : origins) {
            fOut.remove(origin, vertex);
        }
    }

    /**
     * Returns the sources of the receiver. A source is a vertex with no incoming edges. The
     * returned set's iterator traverses the nodes in the order they were added to the graph.
     *
     * @return the sources of the receiver
     */
    public Set<T> getSources() {
        return computeZeroEdgeVertices(fIn);
    }

    /**
     * Returns the sinks of the receiver. A sink is a vertex with no outgoing edges. The returned
     * set's iterator traverses the nodes in the order they were added to the graph.
     *
     * @return the sinks of the receiver
     */
    public Set<T> getSinks() {
        return computeZeroEdgeVertices(fOut);
    }

    private Set<T> computeZeroEdgeVertices(MultiMap<T> map) {
        Set<T> candidates = map.keySet();
        Set<T> roots = new LinkedHashSet<T>(candidates.size());
        for (Iterator<T> it = candidates.iterator(); it.hasNext();) {
            T candidate = it.next();
            if (map.get(candidate).isEmpty()) {
                roots.add(candidate);
            }
        }
        return roots;
    }

    /**
     * Returns the direct children of a vertex. The returned {@link Set} is unmodifiable.
     *
     * @param vertex the parent vertex
     * @return the direct children of <code>vertex</code>
     */
    public Set<T> getChildren(T vertex) {
        return Collections.unmodifiableSet(fOut.get(vertex));
    }

    private boolean hasPath(T start, T end) {
        // break condition
        if (start == end) {
            return true;
        }

        Set<T> children = fOut.get(start);
        for (T child : children) {
            if (hasPath(child, end)) {
                return true;
            }
        }
        return false;
    }

    /*
      * @see java.lang.Object#toString()
      */
    public String toString() {
        return "Out: " + fOut.toString() + " In: " + fIn.toString(); //$NON-NLS-1$ //$NON-NLS-2$
    }

    public void reset() {
        fOut.fMap.clear();
        fIn.fMap.clear();
    }
}