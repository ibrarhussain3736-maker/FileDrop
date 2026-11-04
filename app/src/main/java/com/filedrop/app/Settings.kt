package com.filedrop.app

import android.content.Context

class Settings(ctx: Context) {
    private val p = ctx.getSharedPreferences("filedrop", Context.MODE_PRIVATE)

    var port: Int
        get() = p.getInt("port", 5001)
        set(v) = p.edit().putInt("port", v).apply()

    var folder: String
        get() = p.getString("folder", "FileDrop") ?: "FileDrop"
        set(v) = p.edit().putString("folder", v).apply()

    /** 0 = system, 1 = light, 2 = dark */
    var theme: Int
        get() = p.getInt("theme", 0)
        set(v) = p.edit().putInt("theme", v).apply()

    var autoStart: Boolean
        get() = p.getBoolean("autoStart", false)
        set(v) = p.edit().putBoolean("autoStart", v).apply()

    var keepAwake: Boolean
        get() = p.getBoolean("keepAwake", true)
        set(v) = p.edit().putBoolean("keepAwake", v).apply()

    var lastIp: String
        get() = p.getString("lastIp", "") ?: ""
        set(v) = p.edit().putString("lastIp", v).apply()
}
