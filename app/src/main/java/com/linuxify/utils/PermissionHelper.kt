package com.linuxify.utils

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.Settings

object PermissionHelper { fun openOverlaySettings(activity: Activity) = activity.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${activity.packageName}"))) }
