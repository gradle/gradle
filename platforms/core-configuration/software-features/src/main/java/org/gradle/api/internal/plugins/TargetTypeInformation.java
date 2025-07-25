/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.api.internal.plugins;

public interface TargetTypeInformation<T> {
    class DefinitionTargetTypeInformation<T> implements TargetTypeInformation<T> {
        public final Class<T> definitionType;

        public DefinitionTargetTypeInformation(Class<T> definitionType) {
            this.definitionType = definitionType;
        }

        @Override
        public String toString() {
            return "definition type " + definitionType.getSimpleName();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            DefinitionTargetTypeInformation<?> that = (DefinitionTargetTypeInformation<?>) o;
            return definitionType.equals(that.definitionType);
        }

        @Override
        public int hashCode() {
            return definitionType.hashCode();
        }
    }

    class BuildModelTargetTypeInformation<T extends BuildModel> implements TargetTypeInformation<HasBuildModel<T>> {
        public final Class<T> buildModelType;

        public BuildModelTargetTypeInformation(Class<T> buildModelType) {
            this.buildModelType = buildModelType;
        }

        @Override
        public String toString() {
            return "build model type " + buildModelType.getSimpleName();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            BuildModelTargetTypeInformation<?> that = (BuildModelTargetTypeInformation<?>) o;
            return buildModelType.equals(that.buildModelType);
        }

        @Override
        public int hashCode() {
            return buildModelType.hashCode();
        }
    }
}
