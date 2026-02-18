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

package org.gradle.plugins.ide.internal.tooling;

import org.gradle.tooling.model.dsl.GradleDslBaseScriptModel;
import org.gradle.tooling.model.dsl.GroovyDslBaseScriptModel;
import org.gradle.tooling.model.dsl.KotlinDslBaseScriptModel;
import org.jspecify.annotations.NullMarked;

import java.io.Serializable;

@NullMarked
class DefaultGradleDslBaseScriptModel implements GradleDslBaseScriptModel, Serializable {

    private final DefaultGroovyDslBaseScriptModel groovyDslBaseScriptModel;

    private final DefaultKotlinDslBaseScriptModel kotlinDslBaseScriptModel;

    public DefaultGradleDslBaseScriptModel(
        DefaultGroovyDslBaseScriptModel groovyDslBaseScriptModel,
        DefaultKotlinDslBaseScriptModel kotlinDslBaseScriptModel
    ) {
        this.groovyDslBaseScriptModel = groovyDslBaseScriptModel;
        this.kotlinDslBaseScriptModel = kotlinDslBaseScriptModel;
    }

    @Override
    public GroovyDslBaseScriptModel getGroovyDslBaseScriptModel() {
        return groovyDslBaseScriptModel;
    }

    @Override
    public KotlinDslBaseScriptModel getKotlinDslBaseScriptModel() {
        return kotlinDslBaseScriptModel;
    }

}
