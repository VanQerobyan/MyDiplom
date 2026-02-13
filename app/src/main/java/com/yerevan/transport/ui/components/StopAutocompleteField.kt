package com.yerevan.transport.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenu
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.yerevan.transport.data.local.entity.StopEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StopAutocompleteField(
    label: String,
    value: String,
    suggestions: List<StopEntity>,
    onValueChange: (String) -> Unit,
    onStopSelected: (StopEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val menuExpanded = expanded && suggestions.isNotEmpty()

    ExposedDropdownMenuBox(
        expanded = menuExpanded,
        onExpandedChange = { expanded = it && suggestions.isNotEmpty() },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = {
                onValueChange(it)
                expanded = true
            },
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth(),
            label = { Text(label) },
            singleLine = true,
            trailingIcon = {
                ExposedDropdownMenuDefaults.TrailingIcon(expanded = menuExpanded)
            }
        )
        ExposedDropdownMenu(
            expanded = menuExpanded,
            onDismissRequest = { expanded = false }
        ) {
            suggestions.forEach { stop ->
                DropdownMenuItem(
                    text = { Text(stop.name) },
                    onClick = {
                        onStopSelected(stop)
                        expanded = false
                    }
                )
            }
        }
    }
}
