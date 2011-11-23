/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.api.plugins;

/**
 * <p>Represents an object that is able to accept DSL extensions. A DSL extension is basically a custom namespace in the DSL.
 *
 * <p>To add an extension to this object, you call {@link ExtensionContainer#add(String, Object)} on this
 * object's {@link ExtensionContainer}, passing the extension object. The extension becomes a dynamic property of this target object:</p>
 *
 * <pre autoTested="">
 * extensions.add('custom', new MyPojo())
 *
 * // Extension is available as a property
 * custom.someProperty = 'value'
 *
 * // And as a script block
 * custom {
 *     someProperty = 'value'
 * }
 *
 * class MyPojo {
 *     String someProperty
 * }
 * </pre>
 *
 * <p>Extensions can also be added using a dynamic property accessor on the extension container:
 * {@code project.extensions.myExtension = myPojo} is the same as {@code project.extensions.add('myExtension', myPojo)}.</p>
 *
 * <p>Many Gradle types implement this interface, either statically or dynamically at runtime.</p>
 */
public interface ExtensionAware {
    /**
     * Returns the set of extensions applied to this object.
     */
    ExtensionContainer getExtensions();
}
