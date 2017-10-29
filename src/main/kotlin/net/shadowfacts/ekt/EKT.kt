package net.shadowfacts.ekt

import org.jetbrains.kotlin.cli.common.repl.KotlinJsr223JvmScriptEngineBase
import org.jetbrains.kotlin.cli.common.repl.ReplCompileResult
import java.io.*
import javax.script.*
import kotlin.concurrent.getOrSet

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

	private val lastImportRegex = Regex("(import (.*?)\\n)+")

	private val scriptPrefix = """
val _env = bindings["_env"] as net.shadowfacts.ekt.EKT.TemplateEnvironment
val _result = StringBuilder()
fun echo(it: Any?) { _result.append(it) }
fun include(include: String, data: Map<String, net.shadowfacts.ekt.EKT.TypedValue>? = null) {
	val env = _env.createChild(include, data)
	echo(net.shadowfacts.ekt.EKT.render(env, env.include))
}
fun include(include: String, init: net.shadowfacts.ekt.EKT.DataProvider.() -> Unit) {
	include(include, data = net.shadowfacts.ekt.EKT.DataProvider.init(init))
}
"""
	private val scriptSuffix = """
_result.toString()
"""

	private val engine = ThreadLocal<KotlinJsr223JvmScriptEngineBase>()

	fun render(env: TemplateEnvironment, template: String = env.template): String {
		if (env.cacheDir != null && env.cacheScript.exists()) {
			return eval(env.cacheScript.readText(Charsets.UTF_8), env)
		}

		@Suppress("NAME_SHADOWING")
		var template = template
		val lines = template.split("\n")
		val isImport: (String) -> Boolean = {
			val trimmed = it.trim()
			trimmed.startsWith("[: import") || trimmed.startsWith("[:import")
		}
		val imports = lines.filter(isImport).joinToString("\n") { it.substring(2, it.length - 2) }
		template = lines.filterNot(isImport).joinToString("\n")
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

		val script = imports + scriptPrefix + template + scriptSuffix

		if (env.cacheDir != null) {
			env.cacheScript.apply {
				if (!parentFile.exists()) parentFile.mkdirs()
				if (!exists()) createNewFile()
				writeText(script, Charsets.UTF_8)
			}
		}

		return eval(script, env)
	}

	fun renderFile(name: String, templateDir: File, includeDir: File, cacheDir: File? = null, data: Map<String, TypedValue>): String {
		return render(FileTemplateEnvironment(name, templateDir, includeDir, cacheDir, data))
	}

	fun renderFile(name: String, templateDir: File, includeDir: File, cacheDir: File? = null, init: DataProvider.() -> Unit): String {
		return render(FileTemplateEnvironment(name, templateDir, includeDir, cacheDir, init))
	}

	fun renderFile(name: String, dir: File, cacheScripts: Boolean = false, data: Map<String, TypedValue>): String {
		return renderFile(name, dir, File(dir, "includes"), if (cacheScripts) File(dir, "cache") else null, data)
	}

	fun renderFile(name: String, dir: File, cacheScripts: Boolean = false, init: DataProvider.() -> Unit): String {
		return renderFile(name, dir, File(dir, "includes"), if (cacheScripts) File(dir, "cache") else null, init)
	}

	fun renderClasspath(name: String, templatePath: String, includePath: String, cacheDir: File? = null, data: Map<String, TypedValue>): String {
		return render(ClasspathTemplateEnvironment(name, templatePath, includePath, cacheDir, data))
	}

	fun renderClasspath(name: String, templatePath: String, includePath: String, cacheDir: File? = null, init: DataProvider.() -> Unit): String {
		return render(ClasspathTemplateEnvironment(name, templatePath, includePath, cacheDir, init))
	}

	fun renderClasspath(name: String, path: String, cacheDir: File? = null, data: Map<String, TypedValue>): String {
		return renderClasspath(name, path, "$path/includes", cacheDir, data)
	}

	fun renderClasspath(name: String, path: String, cacheDir: File? = null, init: DataProvider.() -> Unit): String {
		return renderClasspath(name, path, "$path/includes", cacheDir, init)
	}

	private fun eval(script: String, env: TemplateEnvironment): String {
		val engine = engine.getOrSet { ScriptEngineManager().getEngineByExtension("kts") as KotlinJsr223JvmScriptEngineBase }

		val context = createContext(engine, env)

		if (env.cacheDir != null) {
			val cacheCompiled = env.cacheCompiled
			val compiled = if (cacheCompiled.exists()) {
				val fis = FileInputStream(cacheCompiled)
				val ois = ObjectInputStream(fis)
				val data = ois.readObject() as ReplCompileResult.CompiledClasses
				ois.close()
				fis.close()
				KotlinJsr223JvmScriptEngineBase.CompiledKotlinScript(engine, engine.nextCodeLine(context, env.cacheScript.readText(Charsets.UTF_8)), data)
			} else {
				val compiled = engine.compile(script, context) as KotlinJsr223JvmScriptEngineBase.CompiledKotlinScript
				val data = compiled.compiledData
				val fos = FileOutputStream(cacheCompiled)
				val oos = ObjectOutputStream(fos)
				oos.writeObject(data)
				oos.close()
				fos.close()
				compiled
			}
			return engine.eval(compiled, context) as String
		} else {
			return engine.eval(script, context) as String
		}
	}

	private fun eval(script: KotlinJsr223JvmScriptEngineBase.CompiledKotlinScript, env: TemplateEnvironment): String {
		return script.engine.eval(script, createContext(script.engine, env)) as String
	}

	private fun createContext(engine: ScriptEngine, env: TemplateEnvironment): ScriptContext {
		val bindings = engine.createBindings().apply {
			putAll(env.data)
			put("_env", env)
		}

		val context = SimpleScriptContext().apply {
			setBindings(bindings, ScriptContext.ENGINE_SCOPE)
		}

//		Hack to allow data to be accessed by name from template instead of via bindings map
		val unwrapBindings = env.data.keys.joinToString("\n") {
			val type = env.data[it]!!.type
			"val $it = (bindings[\"$it\"] as net.shadowfacts.ekt.EKT.TypedValue).value as $type"
		}
		engine.eval(unwrapBindings, context)

		return context
	}

	interface TemplateEnvironment {
		val rootName: String
		val name: String
		val cacheDir: File?
		val data: Map<String, TypedValue>

		val template: String
		val include: String
		val cacheScript: File
			get() = File(cacheDir!!, "$name.kts")
		val cacheCompiled: File
			get() = File(cacheDir!!, "$name.kts.compiled")

		fun createChild(name: String, data: Map<String, TypedValue>? = null): TemplateEnvironment

	}

	class FileTemplateEnvironment: TemplateEnvironment {
		override val rootName: String
		override val name: String
		override val cacheDir: File?
		override val data: Map<String, TypedValue>

		val templateDir: File
		val includeDir: File

		override val template: String
			get() = File(templateDir, "$name.ekt").readText(Charsets.UTF_8)
		override val include: String
			get() = File(includeDir, "$name.ekt").readText(Charsets.UTF_8)

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

		constructor(name: String, parent: FileTemplateEnvironment, data: Map<String, TypedValue>?) {
			this.rootName = parent.rootName
			this.name = name
			this.templateDir = parent.templateDir
			this.includeDir = parent.includeDir
			this.cacheDir = parent.cacheDir
			this.data = data ?: parent.data
		}

		override fun createChild(name: String, data: Map<String, TypedValue>?): TemplateEnvironment {
			return FileTemplateEnvironment(name, this, data)
		}

	}

	class ClasspathTemplateEnvironment: TemplateEnvironment {
		override val rootName: String
		override val name: String
		override val cacheDir: File?
		override val data: Map<String, TypedValue>

		val templatePath: String
		val includePath: String

		override val template: String
			get() = EKT::class.java.getResourceAsStream("$templatePath/$name.ekt").bufferedReader(Charsets.UTF_8).readText()
		override val include: String
			get() = EKT::class.java.getResourceAsStream("$includePath/$name.ekt").bufferedReader(Charsets.UTF_8).readText()

		constructor(name: String, templatePath: String, includePath: String, cacheDir: File?, data: Map<String, TypedValue>) {
			this.rootName = name
			this.name = name
			this.templatePath = templatePath
			this.includePath = includePath
			this.cacheDir = cacheDir
			this.data = data
		}

		constructor(name: String, templatePath: String, includePath: String, cacheDir: File?, init: DataProvider.() -> Unit):
				this(name, templatePath, includePath, cacheDir, DataProvider.init(init))

		constructor(name: String, parent: ClasspathTemplateEnvironment, data: Map<String, TypedValue>?) {
			this.rootName = parent.rootName
			this.name = name
			this.templatePath = parent.templatePath
			this.includePath = parent.includePath
			this.cacheDir = parent.cacheDir
			this.data = data ?: parent.data
		}

		override fun createChild(name: String, data: Map<String, TypedValue>?): TemplateEnvironment {
			return ClasspathTemplateEnvironment(name, this, data)
		}

	}

	class DataProvider {
		internal val map = mutableMapOf<String, TypedValue>()

		infix fun String.to(value: Any?) {
			if (value is TypedValue) {
				map[this] = value
			} else {
				if (value == null) {
					throw RuntimeException("Must provide explicit type for 'null' value")
				} else {
					map[this] = TypedValue(value, value::class.qualifiedName!!)
				}
			}
		}

		infix fun Any?.asType(type: String): TypedValue {
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

	data class TypedValue(val value: Any?, val type: String)

}