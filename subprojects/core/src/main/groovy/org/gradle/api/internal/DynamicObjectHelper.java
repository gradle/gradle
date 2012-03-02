/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.api.internal;

/**
 * This is necessary because DynamicObjectHelper was renamed to ExtensibleDynamicObject in 1.0-milestone-9.
 *
 * AbstractTask leaked DynamicObjectHelper by having a public method that returned this type. This method
 * has been deprecated but we need to keep a class around with the same name for backwards compatibility.
 *
 * This will probably have to stay until we remove task inheritance.
 */
public class DynamicObjectHelper extends BeanDynamicObject {

    public DynamicObjectHelper(ExtensibleDynamicObject delegate) {
        super(delegate);
    }

}
