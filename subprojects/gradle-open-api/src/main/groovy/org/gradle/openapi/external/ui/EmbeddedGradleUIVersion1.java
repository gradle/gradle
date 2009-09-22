/*
 * Copyright 2009 the original author or authors.
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
package org.gradle.openapi.external.ui;

/**
 This is a Gradle UI that is meant to be embedded in an IDE. Mainly, it has no output tab of its own.
 Use the IDE's output tab since IDEs typically already have their own output windows that are
 searchable, etc.
 
 @author mhunsicker
 */
public interface EmbeddedGradleUIVersion1 extends BasicGradleUIVersion1
{
}
