package com.girlspace.app.ui.feed

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class SavedPostsViewModel @Inject constructor() : ViewModel() {
    // Currently empty — SavedPostsScreen does not require anything yet.
    // But Hilt needs a ViewModel type so hiltViewModel() can resolve.
}
