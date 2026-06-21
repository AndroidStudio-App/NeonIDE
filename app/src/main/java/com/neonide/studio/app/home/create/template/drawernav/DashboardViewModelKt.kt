package com.neonide.studio.app.home.create.template.drawernav

fun DashboardViewModelKt(pkg: String) = """
    package $pkg.ui.dashboard;

    import androidx.lifecycle.LiveData
    import androidx.lifecycle.MutableLiveData
    import androidx.lifecycle.ViewModel

    class DashboardViewModel : ViewModel() {

        private val _text = MutableLiveData<String>().apply {
            value = "This is dashboard Fragment"
        }
        val text: LiveData<String> = _text
    }
""".trimIndent()
