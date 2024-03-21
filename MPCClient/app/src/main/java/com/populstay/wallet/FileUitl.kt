package com.populstay.wallet

import android.content.Context
import java.io.File
import java.io.FileOutputStream


object FileUitl {


     suspend fun copyAssetFilesToFolder(context: Context, assetPath: String, targetFolder: File) {
        val assetManager = context.assets
        val files = assetManager.list(assetPath)

        if (files != null && files.isNotEmpty()) {
            for (file in files) {
                val assetFile = if (assetPath.isNotEmpty()) "$assetPath/$file" else file
                val targetFile = File(targetFolder, file)

                if (assetManager.list(assetFile)?.isNotEmpty() == true) {
                    // 如果是子文件夹，则递归地创建对应的目标子文件夹并继续拷贝
                    val subdir = File(targetFolder, file)
                    subdir.mkdir()
                    copyAssetFilesToFolder(context, assetFile, subdir)
                } else {
                    // 拷贝文件
                    val inputStream = assetManager.open(assetFile)

                    try {
                        val outputStream = FileOutputStream(targetFile)

                        try {
                            val buffer = ByteArray(4 * 1024) // 4KB 缓冲区
                            var bytesRead: Int

                            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                                outputStream.write(buffer, 0, bytesRead)
                            }
                        } finally {
                            outputStream.close()
                        }
                    } finally {
                        inputStream.close()
                    }
                }
            }
        }
    }

    suspend fun copyAssetSubdirectoriesToFolder(context: Context) {
        val assetManager = context.assets
        val targetFolder = File(context.filesDir, "config")

        if (!targetFolder.exists()) {
            targetFolder.mkdir()
        }

        copyAssetFilesToFolder(context, "", targetFolder)
    }

    // todo 副端测试
    suspend fun copyAssetSubdirectoriesToFolderServer(context: Context) {
        val assetManager = context.assets
        val targetFolder = File(context.filesDir, "configServer")

        if (!targetFolder.exists()) {
            targetFolder.mkdir()
        }

        copyAssetFilesToFolder(context, "", targetFolder)
    }

    fun getConfigDir() :String{
        return File(BaseApp.mContext.filesDir, "config").absolutePath
    }

}