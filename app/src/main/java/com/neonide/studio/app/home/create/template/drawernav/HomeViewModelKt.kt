package com.neonide.studio.app.home.create.template.drawernav

fun HomeViewModelKt(pkg: String) = """
    package $pkg.ui.home;

    import androidx.lifecycle.LiveData
    import androidx.lifecycle.MutableLiveData
    import androidx.lifecycle.ViewModel

    class HomeViewModel : ViewModel() {

        private val _text = MutableLiveData<String>().apply {
            value = "This is home Fragment"
        }
        val text: LiveData<String> = _text
    }
""".trimIndent()
