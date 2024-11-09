/*
 * Copyright 2024 the original author or authors.
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
package org.gradle.api.internal.project;

import org.gradle.api.Project;


/**
 *
 * The requirements for equals/hashcode seems to be as follows:
 * <ol>
 *     <li> a {@link DefaultProject} is equal to a {@link MutableStateAccessAwareProject}
 *     even though these are different classes</li>
 *          Testcase: Symmetrical equality between raw and wrapped project
 *     <li>equals needs be tied to object identity.
 *          So {@link Project#getPath()} is not appropriate
 *          TestCase: project identity doesnt survive build finish
 *     </li>
 *     <li> There are also other implementations of {@link Project}
 *     that have other equals implementation of equals</li>
 *     <li> and you obviously can not break the basic contract about #equals
 *              being reflexive, symmetric, transitive ans consistent
 *     </li>
 * </ol>
 *
 * So this class explicitly documents and implements these requirements
 */
class ProjectEquality {

    static Object getEqualityObject(MutableStateAccessAwareProject delegate) {
       return new IdentityObject(delegate.delegate);
    }

    static Object getEqualityObject(ProjectInternal delegate) {
        if (delegate instanceof MutableStateAccessAwareProject) {
            MutableStateAccessAwareProject wrapper = (MutableStateAccessAwareProject) delegate;
            return getEqualityObject(wrapper);
        }
        return new IdentityObject(delegate);
    }

    /**
     * Defines that 2 Projects are considered equals if the {@link DefaultProject} are the same object
     *
     */
    private static class IdentityObject {
        private final Object delegate;

        public IdentityObject(Object delegate) {this.delegate = delegate;}

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            IdentityObject identityObject = (IdentityObject) o;
            return identityObject.delegate == delegate;
        }

        @Override
        public int hashCode() {
            return System.identityHashCode(delegate);
        }
    }
}
