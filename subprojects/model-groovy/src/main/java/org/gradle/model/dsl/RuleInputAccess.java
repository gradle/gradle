/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.model.dsl;

/**
 * Model rules declared in scripts can use this API to access model elements.
 * <p>
 * This API is implicitly mixed in to all model rules.
 * </p>
 * All access model elements will be finalized before returned.
 */
public interface RuleInputAccess {

    /*
        Something like this could serve as our contract with editing tools wanting to help people write build scripts.
     */

    // TODO examples, and more information on where this can be used
    // TODO information on valid model paths
    // TODO information on how arguments to these methods must be literals
    // TODO explain that $() calls that don't map to this interface will be compile time errors (only when receiver is implicit this)

    /**
     * Access the model element at the given path.
     * <p>
     * The type of the returned object is dependent on the model element.
     * As new view type has been specified, the implicit “read only” type will be returned.
     * <p>
     * @param modelPath the path to the model element
     * @return the model element at the given path
     */
    public Object $(String modelPath);

}
