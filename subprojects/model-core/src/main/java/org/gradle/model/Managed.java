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

package org.gradle.model;

import org.gradle.api.Incubating;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * A managed type is transparent to the model space, and enforces immutability at the appropriate times in the object's lifecycle.
 * <p>
 * Gradle generates implementations for managed types.
 * As such, managed types are declared either as interfaces or abstract classes.
 * The generated implementation integrates with the model space mechanisms, and manages mutability.
 * <p>
 * Managed types are mostly behaviour-less, as they are data.
 * Instances of managed types should effectively be considered value objects.
 *
 * <h3>Properties</h3>
 * <p>
 * Managed types declare their structure as properties, via getter and setter methods.
 * Getter and setter methods are expected to conform to the well-known Java Bean naming conventions.
 * A read/write “name” property would be expressed via the following methods:
 * <pre>
 * void setName(String name);
 * String getName();
 * </pre>
 * <p>
 * A getter and setter must be declared for each property that is not of a managed type or of {@link ModelSet}.
 * For properties of managed types or of {@link ModelSet} the getter is mandatory and the setter is optional.
 * If no setter is provided the property is considered inherent and defaults to an "empty" instance of the type.
 * In addition to the traditional getter method, properties of type {@code boolean} (but not {@code Boolean})
 * also support a getter method which name starts with {@code is}, for example:
 *
 * <pre>
 * void setEnabled(boolean enabled);
 * boolean isEnabled();
 * </pre>
 *
 * <h4>Supported property types</h4>
 * <p>
 * The following JDK types are allowed:
 * <ul>
 * <li>{@link String}</li>
 * <li>{@link Boolean}</li>
 * <li>{@link Character}</li>
 * <li>{@link Byte}</li>
 * <li>{@link Short}</li>
 * <li>{@link Integer}</li>
 * <li>{@link Long}</li>
 * <li>{@link Float}</li>
 * <li>{@link Double}</li>
 * <li>{@link java.math.BigInteger}</li>
 * <li>{@link java.math.BigDecimal}</li>
 * <li>{@link java.io.File}</li>
 * </ul>
 * <p>
 * All primitive types and {@link Enum} types are also allowed.
 * <p>
 * Properties that are themselves of a managed type are also supported.
 * <p>
 * Currently, the only collection types that are supported are {@link ModelSet} and {@link ModelMap}, as well as {@link java.util.Set} or {@link java.util.List}
 * of {@link org.gradle.model.internal.manage.schema.extract.ScalarTypes scalar types}, where scalar types is either one of the supported immutable JDK types above or an enumeration.
 * <p>
 * Properties of any other type must have their getter annotated with {@link Unmanaged}.
 * An unmanaged property is not transparent to the model infrastructure and is guaranteed to be immutable when realized.
 *
 * <h3>Named types</h3>
 * <p>
 * Managed types may implement/extend the {@link org.gradle.api.Named} interface.
 * Any managed type implementing this interface will have its {@code name} attribute populated automatically
 * based on the name of the corresponding node in the model graph.
 * <p>
 * The {@link ModelMap} type requires that its elements are {@link org.gradle.api.Named}.
 *
 * <h3>Inheritance</h3>
 * <p>
 * Managed types can be arranged into an inheritance hierarchy.
 * Every type in the hierarchy must conform to the constraints of managed types.
 *
 * <h3>Calculated read-only properties</h3>
 * <p>
 * Managed types can contain getter methods that return calculated values, based on other properties.
 * For example, a “name” property may return the concatenation of a “firstName” and “lastName” property.
 * When using Java 8 or later, such properties can be implemented as interface default methods.
 * Alternatively, the managed type can be implemented as an abstract class with the calculated property implemented as a non-abstract getter method.
 * In both cases, the implementation of the calculated property getter may not call any setter method.
 *
 * <h3>Abstract classes</h3>
 * <p>
 * A managed type can be implemented as an abstract class.
 * All property getters and setters must be declared {@code abstract} (with the exception of calculated read-only properties).
 * The class cannot contain instance variables, constructors, or any methods that are not a getter or setter.
 *
 * <h3>Creating managed model elements</h3>
 * <p>
 * Please see {@link Model} for information on creating model elements of managed types.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Incubating
public @interface Managed {
}
