package com.apps.apkstore.ui.main

import android.app.Application
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import com.apps.apkstore.data.model.AppModel
import com.apps.apkstore.data.model.HomePageData
import com.apps.apkstore.data.repository.AppRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.disposables.CompositeDisposable
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val repository: AppRepository,
    application: Application
) : AndroidViewModel(application) {

    private val disposables = CompositeDisposable()

    val homeData = mutableStateOf<HomePageData?>(null)
    val isLoading = mutableStateOf(false)
    val error = mutableStateOf<String?>(null)
    val searchResults = mutableStateOf<List<AppModel>>(emptyList())
    val isSearching = mutableStateOf(false)

    fun loadHomeData() {
        isLoading.value = true
        error.value = null
        val d = repository.getHomeData()
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({ data ->
                isLoading.value = false
                homeData.value = data
                error.value = null
            }, { e ->
                isLoading.value = false
                error.value = e.message ?: "Failed to load"
            })
        disposables.add(d)
    }

    fun searchApps(query: String) {
        if (query.isBlank()) {
            searchResults.value = emptyList()
            isSearching.value = false
            return
        }
        isSearching.value = true
        val d = repository.searchApps(query, 1, 20)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({ result ->
                isSearching.value = false
                searchResults.value = result.apps
            }, { _ ->
                isSearching.value = false
                searchResults.value = emptyList()
            })
        disposables.add(d)
    }

    fun clearSearch() {
        searchResults.value = emptyList()
        isSearching.value = false
    }

    fun shareApp(app: AppModel, context: android.content.Context) {
        com.apps.apkstore.utils.ShareUtils.shareApp(context, app.id, app.name, app.shortDescription)
    }

    override fun onCleared() {
        super.onCleared()
        disposables.clear()
    }
}
