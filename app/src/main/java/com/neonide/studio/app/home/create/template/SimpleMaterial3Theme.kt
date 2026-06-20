package com.neonide.studio.app.home.create.template

fun SimpleMaterial3Theme(themeName: String, actionBar: Boolean): String = """
    <resources xmlns:tools="http://schemas.android.com/tools">
        <style name="Base.$themeName" parent="Theme.Material3.DayNight${if (!actionBar) ".NoActionBar" else ""}">
        </style>
        <style name="$themeName" parent="Base.$themeName" />
    </resources>
""".trimIndent()
