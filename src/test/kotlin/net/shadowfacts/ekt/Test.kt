package net.shadowfacts.ekt

import java.io.File

/**
 * @author shadowfacts
 */
fun main(args: Array<String>) {
	val res = EKT.render(File("template.ekt"), mapOf(
			"value" to 11
	), dumpGeneratedScript = true)

	File("result.txt").apply {
		if (!exists()) createNewFile()
		writeText(res)
	}
}