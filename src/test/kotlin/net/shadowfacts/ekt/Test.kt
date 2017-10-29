package net.shadowfacts.ekt

import java.io.File
import java.lang.management.ManagementFactory
import kotlin.concurrent.thread

/**
 * @author shadowfacts
 */
fun main(args: Array<String>) {
	File("output.txt").writeText(render())

//	println(ManagementFactory.getRuntimeMXBean().name)
//
//	for (i in 0..99) {
//		println("Rendering $i")
//		render()
//	}
//
//	println("done")

//	thread {
//		for (i in 0..30) {
//			println("Thread 1 iteration $i")
//			render()
//		}
//	}
//
//	thread {
//		for (i in 0..30) {
//			println("Thread 2 iteration $i")
//			render()
//		}
//	}
//
//	thread {
//		for (i in 0..30) {
//			println("Thread 3 iteration $i")
//			render()
//		}
//	}
}

fun render(): String {
	return EKT.renderClasspath("template", "/templates", cacheDir = File("cache")) {
		"list" to (listOf(1, 2, 3) asType "List<Int>")
	}
}