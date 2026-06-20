package com.neonide.studio.app.home.create.template.bottomnav

fun BottomNavNotificationsViewModelKt(pkg: String) = """
    package $pkg.ui.notifications

    import androidx.lifecycle.LiveData
    import androidx.lifecycle.MutableLiveData
    import androidx.lifecycle.ViewModel

    class NotificationsViewModel : ViewModel() {

        private val _text = MutableLiveData<String>().apply {
            value = "This is notifications Fragment"
        }
        val text: LiveData<String> = _text
    }
""".trimIndent()
