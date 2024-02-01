package org.gradle.internal.declarativedsl

annotation class Adding
annotation class Restricted
annotation class Configuring(val propertyName: String = "")
annotation class HasDefaultValue
annotation class Builder

annotation class AccessFromCurrentReceiverOnly
annotation class HiddenInDeclarativeDsl
