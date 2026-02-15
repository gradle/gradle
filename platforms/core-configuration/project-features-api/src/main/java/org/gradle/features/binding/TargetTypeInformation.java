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

package org.gradle.features.binding;

import org.gradle.api.Incubating;

/**
 * Information about the target type of a project feature binding.
 *
 * @param <T> the type of the target
 *
 * @since 9.5.0
 */
@Incubating
public interface TargetTypeInformation<T> {

    /**
     * The name of the target class.
     *
     * @return the name of the target class
     *
     * @since 9.5.0
     */
    String getTargetClassName();

    /**
     * Target type information for binding to a definition type.
     *
     * @param <T> the type of the definition
     *
     * @since 9.5.0
     */
    @Incubating
    class DefinitionTargetTypeInformation<T> implements TargetTypeInformation<T> {
        private final Class<T> definitionType;
        private final String targetClassName;

        /**
         * Constructs a new {@code DefinitionTargetTypeInformation}.
         *
         * @param definitionType the definition type
         *
         * @since 9.5.0
         */
        public DefinitionTargetTypeInformation(Class<T> definitionType) {
            this.definitionType = definitionType;
            this.targetClassName = definitionType.getName();
        }

        /**
         * The definition type.
         *
         * @since 9.5.0
         */
        public Class<T> getDefinitionType() {
            return definitionType;
        }

        @Override
        public String getTargetClassName() {
            return targetClassName;
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

    /**
     * Target type information for binding to a build model type.
     *
     * @param <T> the type of the build model
     *
     * @since 9.5.0
     */
    @Incubating
    class BuildModelTargetTypeInformation<T extends BuildModel> implements TargetTypeInformation<Definition<T>> {
        private final Class<T> buildModelType;
        private final String targetClassName;

        /**
         * Constructs a new {@code BuildModelTargetTypeInformation}.
         *
         * @param buildModelType the build model type
         *
         * @since 9.5.0
         */
        public BuildModelTargetTypeInformation(Class<T> buildModelType) {
            this.buildModelType = buildModelType;
            this.targetClassName = buildModelType.getName();
        }

        /**
         * The build model type.
         *
         * @since 9.5.0
         */
        public Class<T> getBuildModelType() {
            return buildModelType;
        }

        @Override
        public String getTargetClassName() {
            return targetClassName;
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
