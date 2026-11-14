package com.mnavarrosqa.clima.ui

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.mnavarrosqa.clima.data.PlacePhoto
import com.mnavarrosqa.clima.data.WeatherSnapshot
import com.mnavarrosqa.clima.data.tr
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Antes de compartir: la imagen tal como sale, el formato (publicación o historia con los
 * próximos días), la foto de la capital de fondo si hay, y compartir, guardar o mandar solo texto.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareSheet(snapshot: WeatherSnapshot, photo: PlacePhoto?, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var format by rememberSaveable { mutableStateOf(ShareFormat.POST) }
    // Si en la pantalla hay foto, de entrada la imagen también la lleva.
    var withPhoto by rememberSaveable { mutableStateOf(photo != null) }
    var busy by remember { mutableStateOf(false) }
    val options = ShareOptions(format, photo?.takeIf { withPhoto })

    // La vista previa es la misma imagen que se comparte, a tamaño completo y reducida al mostrar.
    val preview by produceState<ImageBitmap?>(null, options, snapshot) {
        value = null
        value = runCatching {
            withContext(Dispatchers.Default) { ShareImage.render(context.applicationContext, snapshot, options).asImageBitmap() }
        }.onFailure { if (it is CancellationException) throw it }.getOrNull()
    }

    fun act(failure: String, block: suspend () -> Unit) {
        if (busy) return
        busy = true
        scope.launch {
            try {
                block()
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (_: Exception) {
                Toast.makeText(context, failure, Toast.LENGTH_SHORT).show()
            } finally {
                busy = false
            }
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                tr("Compartir el pronóstico", "Share the forecast"),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(16.dp))

            // Alto fijo según el formato: al cambiar de formato la hoja no salta mientras se dibuja.
            Box(
                Modifier
                    .heightIn(max = 360.dp)
                    .aspectRatio(format.width.toFloat() / format.height)
                    .clip(ClimaShapes.tile)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                contentAlignment = Alignment.Center,
            ) {
                val shown = preview
                if (shown != null) {
                    Image(
                        shown,
                        contentDescription = tr("Vista previa de la imagen", "Image preview"),
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    CircularProgressIndicator(Modifier.size(32.dp))
                }
            }
            Spacer(Modifier.height(16.dp))

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ShareFormat.entries.forEach { option ->
                    val selected = option == format
                    FilterChip(
                        selected = selected,
                        onClick = { format = option },
                        label = {
                            Text(
                                when (option) {
                                    ShareFormat.POST -> option.label + " · 4:5"
                                    ShareFormat.STORY -> option.label + tr(" · con días", " · with days")
                                },
                            )
                        },
                        leadingIcon = if (selected) {
                            { Icon(Icons.Rounded.Check, contentDescription = null, Modifier.size(FilterChipDefaults.IconSize)) }
                        } else {
                            null
                        },
                    )
                }
            }

            if (photo != null) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(ClimaShapes.inner)
                        .toggleable(value = withPhoto, role = Role.Switch, onValueChange = { withPhoto = it })
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(tr("Foto de la capital de fondo", "Capital city photo background"), style = MaterialTheme.typography.bodyLarge)
                        Text(
                            tr("Con el crédito del autor en la imagen", "With the author's credit on the image"),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Switch(checked = withPhoto, onCheckedChange = null)
                }
            }
            Spacer(Modifier.height(16.dp))

            Button(
                onClick = {
                    act(tr("No pude armar la imagen", "Couldn't create the image")) {
                        ShareImage.share(context, snapshot, options)
                        onDismiss()
                    }
                },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) {
                Icon(Icons.Rounded.Share, contentDescription = null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(tr("Compartir imagen", "Share image"))
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (ShareImage.canSave) {
                    OutlinedButton(
                        onClick = {
                            act(tr("No pude guardar la imagen", "Couldn't save the image")) {
                                val saved = ShareImage.saveToGallery(context, snapshot, options)
                                Toast.makeText(
                                    context,
                                    if (saved) tr("Guardada en Imágenes/Climatin", "Saved to Pictures/Climatin") else tr("No pude guardar la imagen", "Couldn't save the image"),
                                    Toast.LENGTH_SHORT,
                                ).show()
                            }
                        },
                        enabled = !busy,
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                    ) { Text(tr("Guardar", "Save")) }
                }
                OutlinedButton(
                    onClick = {
                        ShareImage.shareText(context, snapshot)
                        onDismiss()
                    },
                    enabled = !busy,
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                ) { Text(tr("Solo texto", "Text only")) }
            }
        }
    }
}
