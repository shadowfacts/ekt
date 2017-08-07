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

	private val startStringRegex = Regex("([:=#])]")
	private val endStringRegex = Regex("\\[([:=#])")

	private val scriptPrefix = """
val _env = bindings["_env"] as net.shadowfacts.ekt.EKT.TemplateEnvironment
val _result = StringBuilder()
fun echo(it: Any) { _result.append(it) }
fun include(include: String) {
	val env = net.shadowfacts.ekt.EKT.TemplateEnvironment(include, _env)
	echo(net.shadowfacts.ekt.EKT.render(env, env.include))
}
"""
	private val scriptSuffix = """
_result.toString()
"""

	private val engine by lazy {
		ScriptEngineManager().getEngineByExtension("kts")
	}

	fun render(env: TemplateEnvironment, template: String = env.template): String {
		if (env.cacheDir != null && env.cacheFile.exists()) {
			return eval(env.cacheFile.readText(Charsets.UTF_8), env)
		}

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

		if (env.cacheDir != null) {
			env.cacheFile.apply {
				if (!parentFile.exists()) parentFile.mkdirs()
				if (!exists()) createNewFile()
				writeText(script, Charsets.UTF_8)
			}
		}

		return eval(script, env)
	}

	fun render(name: String, templateDir: File, includeDir: File, cacheDir: File? = null, data: Map<String, TypedValue>): String {
		return render(TemplateEnvironment(name, templateDir, includeDir, cacheDir, data))
	}

	fun render(name: String, templateDir: File, includeDir: File, cacheDir: File? = null, init: DataProvider.() -> Unit): String {
		return render(TemplateEnvironment(name, templateDir, includeDir, cacheDir, init))
	}

	fun render(name: String, dir: File, cacheScripts: Boolean = false, data: Map<String, TypedValue>): String {
		return render(name, dir, File(dir, "includes"), if (cacheScripts) File(dir, "cache") else null, data)
	}

	fun render(name: String, dir: File, cacheScripts: Boolean = false, init: DataProvider.() -> Unit): String {
		return render(name, dir, File(dir, "includes"), if (cacheScripts) File(dir, "cache") else null, init)
	}

	internal fun eval(script: String, env: TemplateEnvironment): String {
		engine.context = SimpleScriptContext()
		val bindings = engine.getBindings(ScriptContext.ENGINE_SCOPE)
		bindings.putAll(env.data)
		bindings.put("_env", env)

//		Hack to allow data to be accessed by name from template instead of via bindings map
		val unwrapBindings = env.data.keys.map {
			val type = env.data[it]!!.type
			"val $it = (bindings[\"$it\"] as net.shadowfacts.ekt.EKT.TypedValue).value as $type"
		}.joinToString("\n")
		engine.eval(unwrapBindings)

		return engine.eval(script) as String
	}

	class TemplateEnvironment {

		val rootName: String
		val name: String
		val templateDir: File
		val includeDir: File
		val cacheDir: File?
		val data: Map<String, TypedValue>

		val template: String
			get() = File(templateDir, "$name.ekt").readText(Charsets.UTF_8)
		val include: String
			get() = File(includeDir, "$name.ekt").readText(Charsets.UTF_8)
		val cacheFile: File
			get() = File(cacheDir!!, "$name.kts")

		constructor(name: String, templateDir: File, includeDir: File, cacheDir: File?, data: Map<String, TypedValue>) {
			this.rootName = name
			this.name = name
			this.templateDir = templateDir
			this.includeDir = includeDir
			this.cacheDir = cacheDir
			this.data = data
		}

		constructor(name: String, templateDir: File, includeDir: File, cacheDir: File?, init: DataProvider.() -> Unit):
				this(name, templateDir, includeDir, cacheDir, DataProvider.init(init))

		constructor(name: String, parent: TemplateEnvironment, cacheDir: File? = parent.cacheDir) {
			this.rootName = parent.rootName
			this.name = name
			this.templateDir = parent.templateDir
			this.includeDir = parent.includeDir
			this.cacheDir = cacheDir
			this.data = parent.data
		}

	}

	class DataProvider {
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

		companion object {
			fun init(init: DataProvider.() -> Unit): Map<String, TypedValue> {
				val ctx = DataProvider()
				ctx.init()
				return ctx.map
			}
		}
	}

	data class TypedValue(val value: Any, val type: String)

}