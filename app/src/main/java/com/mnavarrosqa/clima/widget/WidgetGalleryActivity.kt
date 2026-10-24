package com.mnavarrosqa.clima.widget

import com.mnavarrosqa.clima.data.tr
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.mnavarrosqa.clima.ui.theme.ClimaTheme

/**
 * "Mis widgets": todos los widgets de Climatin con su vista previa real y un botón para ponerlos
 * en la pantalla de inicio sin buscarlos en el launcher, más los que ya pusiste para editarlos.
 */
class WidgetGalleryActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent { ClimaTheme { WidgetGallery(onBack = ::finish) } }
    }
}

private fun canPin(context: Context): Boolean =
    AppWidgetManager.getInstance(context).isRequestPinAppWidgetSupported

private fun edit(context: Context, appWidgetId: Int) {
    context.startActivity(
        Intent(context, WidgetConfigActivity::class.java).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WidgetGallery(onBack: () -> Unit) {
    val context = LocalContext.current
    // Se relee al volver: puede que hayas agregado o quitado alguno.
    var placed by remember { mutableStateOf(WidgetCatalog.placed(context)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { placed = WidgetCatalog.placed(context) }
    val pinSupported = remember { canPin(context) }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text(tr("Widgets", "Widgets")) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = tr("Volver", "Back"))
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .wrapContentWidth(Alignment.CenterHorizontally)
                .widthIn(max = 720.dp),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding(),
                bottom = padding.calculateBottomPadding() + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (placed.isNotEmpty()) {
                item { Section(tr("En tu pantalla de inicio", "On your home screen")) }
                item {
                    Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = RoundedCornerShape(20.dp)) {
                        Column {
                            placed.forEach { (kind, id) ->
                                ListItem(
                                    headlineContent = { Text(stringResource(kind.nameRes)) },
                                    supportingContent = { Text(stringResource(kind.descriptionRes)) },
                                    trailingContent = {
                                        IconButton(onClick = { edit(context, id) }) {
                                            Icon(Icons.Rounded.Edit, contentDescription = tr("Personalizar", "Customize"))
                                        }
                                    },
                                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                                    modifier = Modifier.clip(RoundedCornerShape(20.dp)),
                                )
                            }
                        }
                    }
                }
            }
            item { Section(tr("Todos los widgets", "All widgets")) }
            if (!pinSupported) {
                item {
                    Text(
                        tr(
                            "Tu launcher no deja agregarlos desde acá: mantené apretado un lugar vacío de la pantalla de inicio, tocá Widgets y buscá Climatin.",
                            "Your launcher can't add them from here: long-press an empty spot on the home screen, tap Widgets and find Climatin.",
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(WidgetCatalog.kinds, key = { it.receiver.name }) { kind ->
                WidgetCard(kind, count = placed.count { it.first == kind }, pinSupported = pinSupported)
            }
        }
    }
}

@Composable
private fun WidgetCard(kind: WidgetKind, count: Int, pinSupported: Boolean) {
    val context = LocalContext.current
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = RoundedCornerShape(28.dp)) {
        Column(Modifier.padding(16.dp)) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .backdrop(PreviewBackdrop.PHOTO)
                    .padding(vertical = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                GlanceWidgetPreview(kind, kind.sizes.medium, WidgetConfig(style = kind.defaultStyle))
            }
            Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(kind.nameRes), style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(kind.descriptionRes),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (count > 0) {
                        Text(
                            if (count == 1) tr("Ya tenés uno puesto", "You have one placed") else tr("Ya tenés $count puestos", "You have $count placed"),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                if (pinSupported) {
                    FilledTonalButton(onClick = {
                        // El launcher pregunta dónde ponerlo; después se puede personalizar con el lápiz.
                        runCatching { AppWidgetManager.getInstance(context).requestPinAppWidget(kind.component(context), null, null) }
                    }) { Text(tr("Agregar", "Add")) }
                } else if (count > 0) {
                    TextButton(onClick = { edit(context, kind.placedIds(context).first()) }) { Text(tr("Editar", "Edit")) }
                }
            }
        }
    }
}

@Composable
private fun Section(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 4.dp, top = 12.dp),
    )
}
