package net.shadowfacts.ekt

import java.io.File
import kotlin.concurrent.thread

/**
 * @author shadowfacts
 */
fun main(args: Array<String>) {
	for (i in 0..2) {
		thread {
			println("Calling from: $i")
			render()
		}
	}
}

fun render(): String {
	return EKT.renderClasspath("template", "/templates") {
		"list" to (listOf(1, 2, 3) asType "List<Int>")
	}
}