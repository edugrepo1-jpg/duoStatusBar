package io.github.kvmy666.duostatusbar.settings

import android.content.Context
import android.content.pm.PackageManager
import java.io.File
import java.security.MessageDigest
import io.github.kvmy666.duostatusbar.BuildConfig

/** Hash verifies transfer. Package and installed signer verify which app can be updated. */
internal object UpdateArtifactVerifier {
    fun validIdentity(appId:String,version:Long,installedVersion:Long,signers:Set<String>,installedSigners:Set<String>) =
        appId==BuildConfig.APPLICATION_ID && version>installedVersion && signers.isNotEmpty() && signers==installedSigners
    fun trusted(context:Context,file:File):Boolean=runCatching {
        val pm=context.packageManager
        val installed=pm.getPackageInfo(context.packageName,PackageManager.GET_SIGNING_CERTIFICATES)
        val archive=pm.getPackageArchiveInfo(file.absolutePath,PackageManager.GET_SIGNING_CERTIFICATES) ?: return false
        fun certificates(info:android.content.pm.PackageInfo)=info.signingInfo?.apkContentsSigners?.map {
            MessageDigest.getInstance("SHA-256").digest(it.toByteArray()).joinToString("") {b->"%02x".format(b.toInt() and 255)}
        }?.toSet().orEmpty()
        validIdentity(archive.packageName,archive.longVersionCode,installed.longVersionCode,certificates(archive),certificates(installed))
    }.getOrDefault(false)
}
