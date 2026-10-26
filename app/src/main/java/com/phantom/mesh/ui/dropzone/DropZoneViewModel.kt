package com.phantom.mesh.ui.dropzone

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.phantom.mesh.data.local.entity.DropZoneEntity
import com.phantom.mesh.data.repository.DropZoneRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class DropZoneViewModel @Inject constructor(
    private val dropZoneRepository: DropZoneRepository
) : ViewModel() {

    val activeDrops: StateFlow<List<DropZoneEntity>> = dropZoneRepository.observeActiveDrops()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun pinFile(file: File, mimeType: String, ttlMinutes: Long = 60, broadcastRadius: Int = 2) {
        viewModelScope.launch {
            dropZoneRepository.pinFile(file, mimeType, ttlMinutes, broadcastRadius)
        }
    }

    fun pullFile(drop: DropZoneEntity) {
        viewModelScope.launch {
            dropZoneRepository.requestPull(drop)
        }
    }
}
