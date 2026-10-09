package com.monopoly.assets

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSBundle
import platform.Foundation.NSString
import platform.Foundation.stringWithContentsOfFile

@OptIn(ExperimentalForeignApi::class)
internal actual fun readResource(path: String): String? {
    val name = path.substringBeforeLast('.')
    val ext = path.substringAfterLast('.', "")
    val file = NSBundle.mainBundle.pathForResource(name, ext) ?: return null
    return NSString.stringWithContentsOfFile(file, encoding = 4u, error = null)
}
