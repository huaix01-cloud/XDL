package com.yg.xdl

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract

/** 调用系统文件管理器打开某个目录 */
object FolderOpener {

    private const val AUTH = "com.android.externalstorage.documents"

    /**
     * 打开外部存储里的目录，比如 "Movies/XDL"。
     * 优先用 DocumentsUI 的目录 URI；不行再退回打开相册。
     */
    fun open(ctx: Context, relPath: String): Boolean {
        val docUri = runCatching {
            DocumentsContract.buildDocumentUri(AUTH, "primary:$relPath")
        }.getOrNull()

        if (docUri != null) {
            val ok = runCatching {
                val i = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(docUri, DocumentsContract.Document.MIME_TYPE_DIR)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                ctx.startActivity(i)
                true
            }.getOrDefault(false)
            if (ok) {
                Log.i("FOLDER", "打开目录 $relPath")
                return true
            }
        }

        // 退回：打开媒体库根
        val fallback = runCatching {
            val i = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(
                    Uri.parse("content://$AUTH/root/primary"),
                    DocumentsContract.Document.MIME_TYPE_DIR
                )
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            ctx.startActivity(i)
            true
        }.getOrDefault(false)
        Log.w("FOLDER", "打开目录失败，退回根目录: $relPath -> $fallback")
        return fallback
    }
}
