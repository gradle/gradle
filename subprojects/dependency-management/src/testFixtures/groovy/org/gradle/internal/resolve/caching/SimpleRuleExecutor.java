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
package org.gradle.internal.resolve.caching;

import org.gradle.api.Transformer;
import org.gradle.api.internal.artifacts.configurations.dynamicversion.CachePolicy;
import org.gradle.internal.reflect.InstantiatingAction;

/**
 * A simple rule executor which actually doesn't perform caching, for test purposes
 */
public class SimpleRuleExecutor<KEY, DETAILS, RESULT> implements CachingRuleExecutor<KEY, DETAILS, RESULT> {

    @Override
    public <D extends DETAILS> RESULT execute(KEY key, InstantiatingAction<DETAILS> action, Transformer<RESULT, D> detailsToResult, Transformer<D, KEY> onCacheMiss, CachePolicy cachePolicy) {
        if (action == null) {
            return null;
        }
        D details = onCacheMiss.transform(key);
        action.execute(details);
        return detailsToResult.transform(details);
    }

}
