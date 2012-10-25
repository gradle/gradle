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

package org.gradle.api.publish;

import org.gradle.api.Incubating;
import org.gradle.api.Named;

/**
 * A publication is a description of an externalised (consumable) view of one or more artifacts, and possibly associated metadata.
 *
 * Different types of publications are different types of representation.
 * For example, the same artifact(s) may be published in a Maven compatible way and in an Ivy compatible way.
 * These would be different publications of the same artifact(s).
 */
@Incubating
public interface Publication extends Named {

}
