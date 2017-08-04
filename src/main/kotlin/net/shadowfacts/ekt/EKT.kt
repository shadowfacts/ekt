package net.shadowfacts.ekt

import java.io.File
import javax.script.ScriptContext
import javax.script.ScriptEngineManager

/**
 * @author shadowfacts
 */
object EKT {

	private val startString = ":]"
	private val endString = "[:"
	private val startStringRegex = Regex("(?:^|[^\\\\])([:=])]\n?")
	private val endStringRegex = Regex("\\[([:=])")

	private val scriptPrefix = """
val _result = StringBuilder()
fun echo(s: Any) { _result.append(s) }
"""
	private val scriptSuffix = """
_result.toString()
"""

	private val manager by lazy {
		ScriptEngineManager()
	}

	fun render(template: String, data: Map<String, Any>): String {
		@Suppress("NAME_SHADOWING")
		var template = template
		template = template.replace("\"", "\\\"")
		template = startString + template + endString
		template = template.replace(startStringRegex, {
			var res = "\necho(\"\"\""
			if (it.groups[1]!!.value == "=") res = ")" + res
			res
		})
		template = template.replace(endStringRegex, {
			var res = "\"\"\")\n"
			if (it.groups[1]!!.value == "=") res += "echo("
			res
		})

		val script = scriptPrefix + template + scriptSuffix

		File("script.kts").apply {
			if (!exists()) createNewFile()
			writeText(script)
		}

		return eval(script, data) as String
	}

	fun render(template: File, data: Map<String, Any>): String {
		return render(template.readText(), data)
	}

	internal fun eval(script: String, data: Map<String, Any> = mapOf()): Any? {
		val engine = manager.getEngineByExtension("kts")
		val bindings = engine.getBindings(ScriptContext.ENGINE_SCOPE)
		bindings.putAll(data)

//		Hack to allow data to be accessed by name from template instead of via bindings map
		val unwrapBindings = data.keys.map {
			val type = data[it]!!::class.qualifiedName
			"val $it = bindings[\"$it\"] as $type;"
		}.joinToString("\n")
		engine.eval(unwrapBindings)

		return engine.eval(script)
	}

}