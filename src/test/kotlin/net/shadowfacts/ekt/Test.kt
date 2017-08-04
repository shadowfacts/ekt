package net.shadowfacts.ekt

import java.io.File

/**
 * @author shadowfacts
 */
fun main(args: Array<String>) {
	val res = EKT.render(File("template.ekt"), mapOf(
			"title" to "EKT Test",
			"body" to "some test content",
			"value" to 42
	), dumpGeneratedScript = File("script.kts"))

	File("result.txt").apply {
		if (!exists()) createNewFile()
		writeText(res)
	}
}