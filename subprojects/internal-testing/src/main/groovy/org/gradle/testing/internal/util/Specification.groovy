/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.testing.internal.util

import groovy.transform.stc.ClosureParams
import groovy.transform.stc.SecondParam

/**
 * This fixes Spock's Mock() and Stub() methods to be understandable by IDEA.
 * By using this subclass, IDEA knows the closure delegate type when using Mock() or Stub().
 * This means that you get editing assistance in said blocks WRT the type being mocked/stubbed.
 */
class Specification extends spock.lang.Specification {

    @SuppressWarnings(['MethodName', 'UnnecessaryOverridingMethod', 'GrUnnecessaryPublicModifier'])
    @Override
    public <T> T Mock(
        @DelegatesTo.Target Class<T> type,
        @DelegatesTo(strategy = Closure.DELEGATE_FIRST, genericTypeIndex = 0)
        @ClosureParams(SecondParam.FirstGenericType) Closure closure
    ) {
        super.Mock(type, closure)
    }

    @SuppressWarnings(['MethodName', 'UnnecessaryOverridingMethod', 'GrUnnecessaryPublicModifier'])
    @Override
    public <T> T Stub(
        @DelegatesTo.Target Class<T> type,
        @DelegatesTo(strategy = Closure.DELEGATE_FIRST, genericTypeIndex = 0)
        @ClosureParams(SecondParam.FirstGenericType) Closure closure
    ) {
        super.Stub(type, closure)
    }

}
