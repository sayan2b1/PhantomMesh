package com.phantom.mesh.ui.topology

import androidx.lifecycle.ViewModel
import com.phantom.mesh.mesh.MeshConnectionManager
import com.phantom.mesh.mesh.MeshRoutingTable
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class TopologyViewModel @Inject constructor(
    private val routingTable: MeshRoutingTable,
    private val meshConnectionManager: MeshConnectionManager
) : ViewModel() {

    val nodes: StateFlow<List<MeshRoutingTable.MeshNode>> = routingTable.topology
    val peerCount: StateFlow<Int> = routingTable.peerCount

    fun restartMesh() {
        meshConnectionManager.restartMesh()
    }
}
