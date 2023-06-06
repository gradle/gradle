/*
 * Copyright 2023 the original author or authors.
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

/**
 * This package is under the extra "meta" postfix, as we are using the "org.gradle.test.precondition"
 * package path by the "dev-prod-data-collector" to collect the precondition probing data.
 *
 * This creates a well-defined of isolation between the precondition probing tests,
 * and actual precondition system testing.
 */
package org.gradle.test.precondition.meta;
