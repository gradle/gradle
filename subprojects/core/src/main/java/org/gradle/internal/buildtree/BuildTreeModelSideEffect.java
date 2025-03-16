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

package org.gradle.internal.buildtree;

import org.jspecify.annotations.NullMarked;

import java.io.Serializable;

/**
 * An internal side effect executed during a build action
 * that must be observed even if the resulting model is loaded from cache.
 * <p>
 * The implementations are serialized by the Configuration Cache.
 *
 * @see BuildTreeModelSideEffectExecutor
 */
@NullMarked
public interface BuildTreeModelSideEffect extends Serializable {

    void runSideEffect();

}
