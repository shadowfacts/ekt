package net.shadowfacts.ekt

import java.io.File
import javax.script.ScriptContext
import javax.script.ScriptEngineManager
import javax.script.SimpleScriptContext

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

	private val engine by lazy {
		manager.getEngineByExtension("kts")
	}

	fun render(template: String, dumpGeneratedScript: File? = null, dataProvider: DataProviderContext.() -> Unit): String {
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

		val data = DataProviderContext()
		data.dataProvider()

		return eval(script, data.map) as String
	}

	fun render(template: File, dumpGeneratedScript: File? = null, dataProvider: DataProviderContext.() -> Unit): String {
		return render(template.readText(), dumpGeneratedScript, dataProvider)
	}

	internal fun eval(script: String, data: Map<String, TypedValue> = mapOf()): Any? {
		engine.context = SimpleScriptContext()
		val bindings = engine.getBindings(ScriptContext.ENGINE_SCOPE)
		bindings.putAll(data)

//		Hack to allow data to be accessed by name from template instead of via bindings map
		val unwrapBindings = data.keys.map {
			val type = data[it]!!.type
			"val $it = (bindings[\"$it\"] as net.shadowfacts.ekt.EKT.TypedValue).value as $type"
		}.joinToString("\n")
		engine.eval(unwrapBindings)

		return engine.eval(script)
	}

	class DataProviderContext {
		internal val map = mutableMapOf<String, TypedValue>()

		infix fun String.to(value: Any) {
			if (value is TypedValue) {
				map[this] = value
			} else {
				map[this] = TypedValue(value, value::class.qualifiedName!!)
			}
		}

		infix fun Any.asType(type: String): TypedValue {
			return TypedValue(this, type)
		}
	}

	data class TypedValue(val value: Any, val type: String)

}