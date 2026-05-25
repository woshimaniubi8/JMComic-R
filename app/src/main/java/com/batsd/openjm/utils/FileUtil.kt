package com.batsd.openjm.utils

import android.content.Context
import android.os.Environment
import java.io.File

/**
 * 文件管理工具
 */
object FileUtil {
    
    /**
     * 获取应用的缓存目录
     */
    fun getCacheDir(context: Context): File {
        return context.cacheDir
    }
    
    /**
     * 获取应用的文件目录
     */
    fun getFilesDir(context: Context): File {
        return context.filesDir
    }
    
    /**
     * 获取下载目录
     */
    fun getDownloadDir(context: Context): File {
        return File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "jmcomic")
            .apply { mkdirs() }
    }
    
    /**
     * 获取图片缓存目录
     */
    fun getImageCacheDir(context: Context): File {
        return File(getCacheDir(context), "images").apply { mkdirs() }
    }
    
    /**
     * 创建文件
     */
    fun createFile(context: Context, directory: File, filename: String): File {
        return File(directory, filename)
    }
    
    /**
     * 删除文件
     */
    fun deleteFile(file: File): Boolean {
        return file.delete()
    }
    
    /**
     * 清空目录
     */
    fun clearDirectory(directory: File) {
        if (directory.isDirectory) {
            directory.listFiles()?.forEach {
                if (it.isDirectory) {
                    clearDirectory(it)
                }
                it.delete()
            }
        }
    }
}
