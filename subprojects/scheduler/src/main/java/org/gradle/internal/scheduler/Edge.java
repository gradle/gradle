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

package org.gradle.internal.scheduler;

public class Edge implements Comparable<Edge> {
    private final Node source;
    private final Node target;
    private final EdgeType type;

    public Edge(Node source, EdgeType type, Node target) {
        this.source = source;
        this.target = target;
        this.type = type;
    }

    public Node getSource() {
        return source;
    }

    public Node getTarget() {
        return target;
    }

    public EdgeType getType() {
        return type;
    }

    public boolean isRemovableToBreakCycles() {
        switch (type) {
            case SHOULD_COMPLETE_BEFORE:
            case AVOID_STARTING_BEFORE_FINALIZED:
                return true;
            default:
                return false;
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Edge edge = (Edge) o;

        if (!source.equals(edge.source)) return false;
        if (!target.equals(edge.target)) return false;
        return type == edge.type;
    }

    @Override
    public int hashCode() {
        int result = source.hashCode();
        result = 31 * result + target.hashCode();
        result = 31 * result + type.hashCode();
        return result;
    }

    @Override
    public int compareTo(Edge o) {
        return type.compareTo(o.type);
    }

    @Override
    public String toString() {
        return String.format("%s --%s--> %s", source, type, target);
    }
}
