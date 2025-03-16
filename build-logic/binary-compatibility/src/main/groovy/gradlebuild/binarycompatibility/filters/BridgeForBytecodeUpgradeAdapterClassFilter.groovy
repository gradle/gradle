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

package gradlebuild.binarycompatibility.filters

import japicmp.filter.ClassFilter
import javassist.CtClass
import org.gradle.api.Generated

/**
 * $$BridgeFor$$ classes generated for bytecode upgrade instrumentation
 */
class BridgeForBytecodeUpgradeAdapterClassFilter implements ClassFilter {

    private static final BRIDGE_CLASS_PREFIX = "\$\$BridgeFor\$\$"

    @Override
    boolean matches(CtClass ctClass) {
        return ctClass.name.contains(BRIDGE_CLASS_PREFIX) && ctClass.hasAnnotation(Generated.class)
    }
}
