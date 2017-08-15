/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.tooling.internal.consumer.connection;

import org.gradle.api.Action;
import org.gradle.tooling.BuildController;
import org.gradle.tooling.UnknownModelException;
import org.gradle.tooling.UnsupportedVersionException;
import org.gradle.tooling.model.Model;
import org.gradle.tooling.model.gradle.GradleBuild;

abstract class AbstractBuildController extends HasCompatibilityMapping implements BuildController {
    public <T> T getModel(Class<T> modelType) throws UnknownModelException {
        return getModel(null, modelType);
    }

    public <T> T findModel(Class<T> modelType) {
        return findModel(null, modelType);
    }

    public GradleBuild getBuildModel() {
        return getModel(null, GradleBuild.class);
    }

    public <T> T getModel(Model target, Class<T> modelType) throws UnknownModelException {
        return getModel(target, modelType, null, null);
    }

    public <T> T findModel(Model target, Class<T> modelType) {
        return findModel(target, modelType, null, null);
    }

    public <T, P> T getModel(Class<T> modelType, Class<P> parameterType, Action<? super P> parameterInitializer) throws UnsupportedVersionException {
        return getModel(null, modelType, parameterType, parameterInitializer);
    }

    public <T, P> T findModel(Class<T> modelType, Class<P> parameterType, Action<? super P> parameterInitializer) {
        return findModel(null, modelType, parameterType, parameterInitializer);
    }

    public <T, P> T findModel(Model target, Class<T> modelType, Class<P> parameterType, Action<? super P> parameterInitializer) {
        try {
            return getModel(target, modelType, parameterType, parameterInitializer);
        } catch (UnsupportedVersionException e) {
            // Ignore
            return null;
        }
    }
}
