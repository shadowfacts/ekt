package net.shadowfacts.ekt

import java.io.File
import javax.script.ScriptContext
import javax.script.ScriptEngineManager

/**
 * @author shadowfacts
 */
object EKT {

	private val startControlCodes: Map<String, (String) -> String> = mapOf(
			":" to { s -> s },
			"=" to { s -> ")" + s },
			"#" to { s -> "*/" + s }
	)
	private val endControlCodes: Map<String, (String) -> String> = mapOf(
			":" to { s -> s },
			"=" to { s -> s + "echo(" },
			"#" to { s -> s + "/*" }
	)

	private val startStringRegex = Regex("(?:^|[^\\\\])([:=#])]")
	private val endStringRegex = Regex("\\[([:=#])")

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

	fun render(template: String, data: Map<String, Any>, dumpGeneratedScript: File? = null): String {
		@Suppress("NAME_SHADOWING")
		var template = template
		template = template.replace("$", "\${'$'}")
		template = ":]$template[:"
		template = template.replace(startStringRegex, {
			val c = it.groups[1]!!.value
			if (c in startControlCodes) {
				startControlCodes[c]!!("\necho(\"\"\"")
			} else {
				throw RuntimeException("Unknown control code: [$c")
			}
		})
		template = template.replace(endStringRegex, {
			val c = it.groups[1]!!.value
			if (c in endControlCodes) {
				endControlCodes[c]!!("\"\"\")\n")
			} else {
				throw RuntimeException("Unknown control code: $c]")
			}
		})

		val script = scriptPrefix + template + scriptSuffix

		dumpGeneratedScript?.apply {
			if (!exists()) createNewFile()
			writeText(script)
		}

		return eval(script, data) as String
	}

	fun render(template: File, data: Map<String, Any>, dumpGeneratedScript: File? = null): String {
		return render(template.readText(), data, dumpGeneratedScript)
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