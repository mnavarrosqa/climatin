package com.mnavarrosqa.clima.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mnavarrosqa.clima.data.PlacePhoto
import com.mnavarrosqa.clima.data.tr

/** La foto de fondo ya decodificada, con la página (ciudad) a la que pertenece. */
class ShownPhoto(val placeId: String, val photo: PlacePhoto, val bitmap: ImageBitmap)

/** El desenfoque solo existe desde Android 12: antes la foto queda nítida y hace falta más velo. */
private val canBlur = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

/**
 * Velo de la foto. Las tarjetas mandan: arriba, detrás de la temperatura grande, es más fuerte, y
 * nunca baja de 0.45. Encima va además el velo propio del pronóstico.
 */
private val PhotoVeil: Brush = run {
    val extra = if (canBlur) 0f else 0.12f
    Brush.verticalGradient(
        0f to Color.Black.copy(alpha = 0.62f + extra),
        0.35f to Color.Black.copy(alpha = 0.48f + extra),
        1f to Color.Black.copy(alpha = 0.52f + extra),
    )
}

/**
 * La foto del lugar a pantalla completa, desenfocada y oscurecida: se adivina el lugar sin que
 * compita con los datos. [particles] va entre la foto y el velo (la lluvia o la nieve del cielo).
 */
@Composable
fun PhotoBackdrop(bitmap: ImageBitmap, modifier: Modifier = Modifier, particles: @Composable () -> Unit = {}) {
    Box(modifier) {
        Image(
            bitmap = bitmap,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .blur(20.dp),
        )
        particles()
        Box(Modifier.fillMaxSize().background(PhotoVeil))
    }
}

/** Crédito de la foto (lo piden las licencias): autor, licencia y Commons. Al tocarlo abre la página del archivo. */
@Composable
fun PhotoCredit(photo: PlacePhoto, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val parts = listOfNotNull(
        photo.artist.takeIf { it.isNotBlank() },
        photo.license.takeIf { it.isNotBlank() },
        "Wikimedia Commons",
    )
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        SkyGlass(
            shape = ClimaShapes.inner,
            modifier = Modifier
                .clip(ClimaShapes.inner)
                .clickable(
                    enabled = photo.descriptionUrl.isNotBlank(),
                    onClickLabel = tr("Ver la foto en Wikimedia Commons", "View the photo on Wikimedia Commons"),
                    role = Role.Button,
                ) {
                    // Sin navegador no hay a dónde ir: el crédito igual queda a la vista.
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(photo.descriptionUrl))) }
                },
        ) {
            Text(
                // La foto es de la capital, no del lugar exacto: se dice cuál es.
                listOfNotNull(photo.title.takeIf { it.isNotBlank() }, tr("Foto: ", "Photo: ") + parts.joinToString(" · ")).joinToString(" · "),
                style = MaterialTheme.typography.labelMedium,
                color = SkySurface.contentVariant,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            )
        }
    }
}
