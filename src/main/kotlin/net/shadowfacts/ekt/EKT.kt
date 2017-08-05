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

	fun render(template: String, scriptCache: File? = null, data: Map<String, TypedValue>): String {
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

//		Hack to allow data to be accessed by name from template instead of via bindings map
		val unwrapBindings = data.keys.map {
			val type = data[it]!!.type
			"val $it = (bindings[\"$it\"] as net.shadowfacts.ekt.EKT.TypedValue).value as $type"
		}.joinToString("\n")

		val script = unwrapBindings + scriptPrefix + template + scriptSuffix

		scriptCache?.apply {
			if (!parentFile.exists()) parentFile.mkdirs()
			if (!exists()) createNewFile()
			writeText(script)
		}

		return eval(script, data) as String
	}

	fun render(template: String, scriptCache: File? = null, dataProvider: DataProviderContext.() -> Unit): String {
		val ctx = DataProviderContext()
		ctx.dataProvider()
		return render(template, scriptCache, ctx.map)
	}

	fun render(template: File, scriptCacheDir: File? = null, data: Map<String, TypedValue>): String {
		val cacheFile = if (scriptCacheDir != null) {
			File(scriptCacheDir, template.nameWithoutExtension + ".kts")
		} else {
			null
		}

		if (cacheFile != null && cacheFile.exists()) {
			return eval(cacheFile.readText(), data) as String
		} else {
			return render(template.readText(), cacheFile, data)
		}
	}

	fun render(template: File, scriptCacheDir: File? = null, dataProvider: DataProviderContext.() -> Unit): String {
		val ctx = DataProviderContext()
		ctx.dataProvider()
		return render(template, scriptCacheDir, ctx.map)
	}

	internal fun eval(script: String, data: Map<String, TypedValue> = mapOf()): Any? {
		engine.context = SimpleScriptContext()
		val bindings = engine.getBindings(ScriptContext.ENGINE_SCOPE)
		bindings.putAll(data)

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