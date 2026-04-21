package com.thalock.app.ui.screens.folder

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.thalock.app.data.database.AppDatabase
import com.thalock.app.data.model.Document
import com.thalock.app.data.model.DocumentCategory
import com.thalock.app.data.repository.DocumentRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class FolderDetailViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = DocumentRepository(
        AppDatabase.getInstance(application).documentDao()
    )

    private val _category = MutableStateFlow<DocumentCategory?>(null)
    val category: StateFlow<DocumentCategory?> = _category

    val documents: StateFlow<List<Document>> = _category
        .flatMapLatest { cat ->
            repository.getAllDocuments().map { docs ->
                if (cat == null) emptyList()
                else docs.filter { DocumentCategory.forDocumentType(it.documentType) == cat }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setCategory(category: DocumentCategory) {
        _category.value = category
    }
}
