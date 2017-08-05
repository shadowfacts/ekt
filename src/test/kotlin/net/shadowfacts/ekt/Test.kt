package net.shadowfacts.ekt

import java.io.File

/**
 * @author shadowfacts
 */
fun main(args: Array<String>) {
	val res = EKT.render(File("template.ekt"), mapOf(
			"list" to EKT.Value(listOf(1, 2, 3), "List<String>")
	), dumpGeneratedScript = File("script.kts"))

	File("result.txt").apply {
		if (!exists()) createNewFile()
		writeText(res)
	}
}