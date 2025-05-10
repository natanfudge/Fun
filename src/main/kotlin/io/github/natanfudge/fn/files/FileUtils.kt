package io.github.natanfudge.fn.files

import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readString
import kotlin.io.path.readText

fun Path.readString() = SystemFileSystem.source(this).buffered().readString()
//
//fun Path.readString() = toNio().readText()