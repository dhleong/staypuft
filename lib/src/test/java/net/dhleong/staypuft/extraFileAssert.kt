package net.dhleong.staypuft

import assertk.Assert
import assertk.assertions.support.expected
import java.io.File

/**
 * @author dhleong
 */
fun Assert<File>.doesNotExist() {
    if (!actual.exists()) return
    expected("to NOT exist")
}

fun Assert<File>.hasLength(expectedLength: Long) {
    if (actual.exists() && actual.length() == expectedLength) return
    expected("length() == $expectedLength but was: ${actual.length()}")
}
