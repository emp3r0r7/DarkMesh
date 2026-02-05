package com.geeksville.mesh.ui.share

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.geeksville.mesh.service.ServiceAction
import com.geeksville.mesh.service.ServiceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import org.meshtastic.proto.AdminProtos
import javax.inject.Inject

@HiltViewModel
class SharedContactViewModel
@Inject
constructor(
    private val serviceRepository: ServiceRepository,
) : ViewModel() {

    fun addSharedContact(sharedContact: AdminProtos.SharedContact) =
        viewModelScope.launch { serviceRepository.onServiceAction(ServiceAction.ImportContact(sharedContact)) }
}
