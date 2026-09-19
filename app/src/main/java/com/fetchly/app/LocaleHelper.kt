package com.fetchly.app

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

object LocaleHelper {

    fun wrap(context: Context): Context {
        return try {
            val lang = Prefs.getLanguage(context)
            if (lang == "system") return context
            val locale = Locale.forLanguageTag(lang)
            Locale.setDefault(locale)
            val config = Configuration(context.resources.configuration)
            config.setLocale(locale)
            context.createConfigurationContext(config)
        } catch (e: Exception) {
            context
        }
    }
}
