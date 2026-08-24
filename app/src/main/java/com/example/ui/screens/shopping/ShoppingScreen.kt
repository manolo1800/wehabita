package com.example.ui.screens.shopping

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.entities.NeedItemEntity
import com.example.data.local.entities.NeedListEntity
import com.example.ui.components.MemberAvatar
import com.example.ui.theme.*
import com.example.viewmodel.WeHabitaViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShoppingScreen(
    viewModel: WeHabitaViewModel
) {
    val needLists by viewModel.needLists.collectAsState()
    val members by viewModel.homeMembers.collectAsState()
    val currentHome by viewModel.currentHome.collectAsState()
    val userMap = members.associate { it.user.id to it.user }

    LaunchedEffect(currentHome?.id) {
        viewModel.refreshShopping()
    }

    var selectedList by remember { mutableStateOf<NeedListEntity?>(null) }
    var showCreateListDialog by remember { mutableStateOf(false) }
    var showAddItemDialog by remember { mutableStateOf(false) }
    var filterStatus by remember { mutableStateOf("all") }

    val canCreateNeeds = viewModel.hasPrivilege("crear_necesidades")

    // Update selectedList if list changes
    LaunchedEffect(needLists) {
        if (selectedList == null && needLists.isNotEmpty()) {
            selectedList = needLists.first()
        } else if (selectedList != null && !needLists.any { it.id == selectedList?.id }) {
            selectedList = needLists.firstOrNull()
        }
    }

    val itemsFlow = remember(selectedList) {
        if (selectedList != null) viewModel.getItemsForList(selectedList!!.id)
        else kotlinx.coroutines.flow.flowOf(emptyList())
    }
    val currentItems by itemsFlow.collectAsState(initial = emptyList())

    val filteredItems = currentItems.filter {
        when (filterStatus) {
            "pending" -> it.estado == "pendiente"
            "bought" -> it.estado == "comprado"
            else -> true
        }
    }

    Scaffold(
        floatingActionButton = {
            if (canCreateNeeds && selectedList != null) {
                FloatingActionButton(
                    onClick = { showAddItemDialog = true },
                    containerColor = TealTertiary,
                    contentColor = Color.White,
                    modifier = Modifier.testTag("fab_add_need_item")
                ) {
                    Icon(Icons.Filled.AddShoppingCart, contentDescription = "Añadir Producto")
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Listas de Necesidades",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Productos, despensa y compras asignadas",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (canCreateNeeds) {
                    FilledTonalButton(
                        onClick = { showCreateListDialog = true },
                        colors = ButtonDefaults.filledTonalButtonColors(containerColor = TealTertiaryContainer)
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Nueva Lista", color = TealTertiary, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Lists selector chips row
            if (needLists.isNotEmpty()) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(needLists) { list ->
                        val isSelected = selectedList?.id == list.id
                        FilterChip(
                            selected = isSelected,
                            onClick = { selectedList = list },
                            label = { Text(list.titulo, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                            leadingIcon = {
                                Icon(
                                    if (isSelected) Icons.Filled.ShoppingBag else Icons.Outlined.ShoppingBag,
                                    contentDescription = null,
                                    tint = if (isSelected) TealTertiary else MaterialTheme.colorScheme.outline
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = TealTertiaryContainer,
                                selectedLabelColor = TealTertiary
                            )
                        )
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
            }

            // Filter by pending / bought (Responsive Horizontal Scroll)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = filterStatus == "all",
                    onClick = { filterStatus = "all" },
                    label = { Text("Todos (${currentItems.size})", maxLines = 1, softWrap = false) }
                )
                FilterChip(
                    selected = filterStatus == "pending",
                    onClick = { filterStatus = "pending" },
                    label = { Text("Pendientes (${currentItems.count { it.estado == "pendiente" }})", maxLines = 1, softWrap = false) }
                )
                FilterChip(
                    selected = filterStatus == "bought",
                    onClick = { filterStatus = "bought" },
                    label = { Text("Comprados (${currentItems.count { it.estado == "comprado" }})", maxLines = 1, softWrap = false) }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (needLists.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Outlined.ShoppingCart,
                            contentDescription = null,
                            tint = TealTertiary,
                            modifier = Modifier.size(54.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "No hay listas de necesidades creadas",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = { showCreateListDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = TealTertiary)
                        ) {
                            Text("Crear Primera Lista")
                        }
                    }
                }
            } else if (filteredItems.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No hay productos en esta vista.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(bottom = 80.dp)
                ) {
                    items(filteredItems) { item ->
                        val isBought = item.estado == "comprado"
                        val buyer = item.comprador_id?.let { userMap[it] }

                        Card(
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isBought) SuccessContainer.copy(alpha = 0.35f) else MaterialTheme.colorScheme.surface
                            ),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (isBought) SuccessGreen.copy(alpha = 0.3f) else MaterialTheme.colorScheme.outlineVariant
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("need_item_${item.id}")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = isBought,
                                    onCheckedChange = { isChecked ->
                                        viewModel.updateNeedItemStatus(
                                            item = item,
                                            newStatus = if (isChecked) "comprado" else "pendiente"
                                        )
                                    },
                                    colors = CheckboxDefaults.colors(checkedColor = TealTertiary)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "${item.producto_nombre}  ×${item.cantidad}",
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Bold,
                                        textDecoration = if (isBought) TextDecoration.LineThrough else TextDecoration.None,
                                        color = if (isBought) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                                    )
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            text = "Fecha límite: ${item.fecha_necesaria}",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        if (buyer != null) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                MemberAvatar(name = buyer.nombre, size = 16)
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(
                                                    text = "Resp: ${buyer.nombre.split(" ").first()}",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = TealTertiary
                                                )
                                            }
                                        }
                                    }
                                    if (!item.notas.isNullOrBlank()) {
                                        Text(
                                            text = "Nota: ${item.notas}",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                IconButton(
                                    onClick = { viewModel.deleteNeedItem(item.id) }
                                ) {
                                    Icon(
                                        Icons.Filled.DeleteOutline,
                                        contentDescription = "Eliminar",
                                        tint = MaterialTheme.colorScheme.outline,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Create List Dialog
    if (showCreateListDialog) {
        var listTitle by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreateListDialog = false },
            title = { Text("Nueva Lista de Necesidades") },
            text = {
                OutlinedTextField(
                    value = listTitle,
                    onValueChange = { listTitle = it },
                    label = { Text("Nombre de la lista (ej. Supermercado)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (listTitle.isNotBlank()) {
                            viewModel.createNeedList(listTitle.trim()) {
                                showCreateListDialog = false
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = TealTertiary)
                ) {
                    Text("Crear Lista")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateListDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }

    // Add Item Dialog
    if (showAddItemDialog && selectedList != null) {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val todayStr = dateFormat.format(Date())

        var prodName by remember { mutableStateOf("") }
        var prodQty by remember { mutableStateOf("1") }
        var targetDate by remember { mutableStateOf(todayStr) }
        var selectedBuyerId by remember { mutableStateOf<Int?>(null) }
        var prodNotes by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showAddItemDialog = false },
            title = { Text("Añadir a ${selectedList!!.titulo}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = prodName,
                        onValueChange = { prodName = it },
                        label = { Text("Nombre del Producto *") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = prodQty,
                            onValueChange = { prodQty = it },
                            label = { Text("Cantidad") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = targetDate,
                            onValueChange = { targetDate = it },
                            label = { Text("Fecha Límite") },
                            modifier = Modifier.weight(1.5f),
                            singleLine = true
                        )
                    }

                    Text("Responsable de comprar/obtener:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(members) { m ->
                            val isSelected = selectedBuyerId == m.user.id
                            FilterChip(
                                selected = isSelected,
                                onClick = { selectedBuyerId = if (isSelected) null else m.user.id },
                                label = { Text(m.user.nombre.split(" ").first()) },
                                leadingIcon = { MemberAvatar(name = m.user.nombre, size = 16) }
                            )
                        }
                    }

                    OutlinedTextField(
                        value = prodNotes,
                        onValueChange = { prodNotes = it },
                        label = { Text("Notas (marca, tienda, etc.)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (prodName.isNotBlank()) {
                            viewModel.addNeedItem(
                                listaId = selectedList!!.id,
                                productoNombre = prodName.trim(),
                                cantidad = prodQty.toIntOrNull() ?: 1,
                                fechaNecesaria = targetDate,
                                compradorId = selectedBuyerId,
                                notas = prodNotes.ifBlank { null }
                            ) {
                                showAddItemDialog = false
                            }
                        }
                    },
                    enabled = prodName.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = TealTertiary)
                ) {
                    Text("Añadir")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddItemDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }
}
