package com.neonide.studio.editor.bottomsheet

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class BottomSheetViewModel : ViewModel() {

    private val _buildOutput = MutableLiveData("")
    val buildOutput: LiveData<String> = _buildOutput

    private val _status = MutableLiveData<String?>(null)
    val status: LiveData<String?> = _status

    private val _isBuilding = MutableLiveData(false)
    val isBuilding: LiveData<Boolean> = _isBuilding

    private val _selectedTab = MutableLiveData(0)
    val selectedTab: LiveData<Int> = _selectedTab

    fun setBuildOutput(text: String) = _buildOutput.postValue(text)
    fun setStatus(text: String?) = _status.postValue(text)
    fun setIsBuilding(value: Boolean) = _isBuilding.postValue(value)
    fun setSelectedTab(index: Int) = _selectedTab.postValue(index)
}
