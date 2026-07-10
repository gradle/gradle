package org.gradle.smoketest.kotlin.android

import org.junit.Before
import org.junit.Test

class StringPrinterTest {

    lateinit private var stringPrinter: StringPrinterFragment

    @Before fun setUp() {
        stringPrinter = StringPrinterFragment()
    }

    @Test fun shouldPrintStringLength() {
        stringPrinter.printStringLength("Hello world!")
    }
}
