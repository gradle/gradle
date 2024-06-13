package org.gradle.client.demo.mutations

import org.gradle.declarative.dsl.schema.DataClass
import org.gradle.internal.declarativedsl.dom.mutation.TypedMember
import org.gradle.internal.declarativedsl.schemaUtils.propertyNamed

/*
 * This is a missing utility that is going to appear in the Gradle API.
 * We need to replace it with the API one once it's there
 */
fun DataClass.property(name: String): TypedMember.TypedProperty =
    TypedMember.TypedProperty(this, propertyNamed(name))
