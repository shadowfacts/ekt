package net.shadowfacts.ekt

import java.io.File

/**
 * @author shadowfacts
 */
fun main(args: Array<String>) {
	val res = EKT.renderClasspath("template", "/templates", cacheDir = File("cache")) {
		"list" to (listOf(1, 2, 3) asType "List<Int>")
	}

	File("result.txt").apply {
		if (!exists()) createNewFile()
		writeText(res)
	}
}