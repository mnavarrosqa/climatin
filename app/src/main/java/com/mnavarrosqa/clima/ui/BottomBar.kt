package com.mnavarrosqa.clima.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mnavarrosqa.clima.data.tr
import kotlin.math.abs
import kotlin.math.roundToInt

/** Pestañas de la barra de abajo. */
enum class NavTab { CLIMA, MAPA, CIUDADES, AJUSTES }

/**
 * Lo que tapa la barra flotante abajo (alto y margen): las listas lo suman a su relleno de abajo
 * para que lo último se pueda leer. Sin barra, 0. No cambia cuando la barra se esconde al
 * desplazar: si cambiara, las listas saltarían.
 */
val LocalBottomBarSpace = compositionLocalOf { 0.dp }

/** Alto de la píldora. */
private val BarHeight: Dp = 56.dp

/** Alto de la barra más su margen con el borde (12 dp arriba y abajo, lo que pone la raíz). */
val BottomBarSpace: Dp = BarHeight + 24.dp

/** Ancho desde el que la barra va de costado, como riel (plegable abierto, tablet, teléfono apaisado). */
val NavRailMinWidth: Dp = 720.dp

/** Ancho del riel. */
private val NavRailWidth: Dp = 72.dp

/** Ancho del riel más sus márgenes. */
val NavRailSpace: Dp = NavRailWidth + 24.dp

/**
 * Lo que ocupa el riel del lado de inicio, o 0 si la barra va abajo. La raíz ya corre el contenido
 * de las pantallas: no hay que sumarlo de nuevo. Sirve para lo que se dibuja fuera de ese contenido
 * (o para saber si la barra está de costado).
 */
val LocalNavRailSpace = compositionLocalOf { 0.dp }

/** Cuántas "pestañas" de ancho suma la elegida: lleva el nombre al lado del ícono. */
private const val SELECTED_EXTRA = 2f

/** Margen de la píldora con sus pestañas. */
private val PillPadding = 6.dp

/** La marca de la elegida en la píldora: su alto, su margen con la pestaña y su relleno. */
private val IndicatorHeight = 40.dp
private val IndicatorInset = 4.dp
private val IndicatorPadding = 12.dp

/** Separación entre ícono y nombre en la píldora. */
private val LabelGap = 6.dp

/** El riel: alto de cada pestaña, hueco entre ellas y margen arriba y abajo. */
private val RailItemHeight = 60.dp
private val RailItemGap = 4.dp
private val RailPadding = 8.dp

/**
 * Barra de navegación flotante y translúcida, como una píldora sobre el contenido. Sobre el cielo
 * va como vidrio oscuro con íconos blancos; sobre Ciudades y Ajustes toma los colores del tema.
 * La marca de la elegida se desliza de una pestaña a otra con un resorte; en la píldora solo la
 * elegida muestra su nombre (al lado del ícono) y las otras quedan en ícono. Con [vertical] es un
 * riel para el borde de inicio en pantallas anchas, con ícono y nombre en todas.
 *
 * [icon] recibe la pestaña, el color y si está elegida (para el ícono lleno o de contorno).
 */
@Composable
fun FloatingNavBar(
    selected: NavTab,
    overSky: Boolean,
    onSelect: (NavTab) -> Unit,
    mapEnabled: Boolean,
    icon: @Composable (NavTab, Color, Boolean) -> Unit,
    modifier: Modifier = Modifier,
    vertical: Boolean = false,
) {
    val container by animateColorAsState(
        // Sobre el cielo, el mismo vidrio que las tarjetas pero más opaco: su texto blanco pasa
        // por detrás y con menos se mezclaba con el de la barra.
        if (overSky) SkySurface.bar else MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.92f),
        label = "barra fondo",
    )
    val selectedContent = if (overSky) SkySurface.content else MaterialTheme.colorScheme.onSecondaryContainer
    val otherContent = if (overSky) SkySurface.contentVariant else MaterialTheme.colorScheme.onSurfaceVariant
    val indicator = if (overSky) Color.White.copy(alpha = 0.22f) else MaterialTheme.colorScheme.secondaryContainer
    val border = if (overSky) SkySurface.barBorder else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    // Dónde está la marca, en pestañas (0 = Clima). Se lee solo al medir y dibujar: el resorte no
    // recompone la barra en cada cuadro. Casi sin rebote: con rebote la marca se pasaba de largo.
    val position = animateFloatAsState(
        selected.ordinal.toFloat(),
        spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow),
        label = "pestaña elegida",
    )
    val view = LocalView.current
    val select: (NavTab) -> Unit = { tab ->
        // Un toque leve al cambiar; volver a tocar la elegida hace otra cosa (subir) y no vibra.
        if (tab != selected) Haptics.tab(view)
        onSelect(tab)
    }
    Surface(
        modifier = if (vertical) modifier.width(NavRailWidth) else modifier.widthIn(max = 440.dp).fillMaxWidth(),
        shape = RoundedCornerShape(if (vertical) 32.dp else BarHeight / 2),
        color = container,
        border = BorderStroke(1.dp, border),
        shadowElevation = 8.dp,
    ) {
        if (vertical) {
            RailTabs(selected, position, mapEnabled, selectedContent, otherContent, indicator, select, icon)
        } else {
            PillTabs(selected, position, mapEnabled, selectedContent, otherContent, indicator, select, icon)
        }
    }
}

/**
 * Las pestañas de la píldora. El ancho se reparte según [position]: la elegida vale
 * 1 + [SELECTED_EXTRA] y las otras 1; en el medio de un cambio el extra se pasa de una a la otra,
 * así el total no cambia y la marca acompaña el borde de las pestañas sin saltos.
 */
@Composable
private fun PillTabs(
    selected: NavTab,
    position: State<Float>,
    mapEnabled: Boolean,
    selectedContent: Color,
    otherContent: Color,
    indicator: Color,
    onSelect: (NavTab) -> Unit,
    icon: @Composable (NavTab, Color, Boolean) -> Unit,
) {
    val tabs = NavTab.entries
    val count = tabs.size
    // Cuánto se ve el nombre de cada pestaña: 1 la elegida, 0 las otras, intermedio mientras pasa.
    fun reveal(index: Int): Float {
        val at = position.value.coerceIn(0f, count - 1f)
        return (1f - abs(index - at)).coerceAtLeast(0f)
    }
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .height(BarHeight)
            .padding(horizontal = PillPadding),
    ) {
        // Lo que entra del nombre en la elegida: su ancho menos ícono, márgenes y separación. Con
        // letra grande se corta con "…" en vez de pasarse de la pestaña.
        val labelMax = (maxWidth / (count + SELECTED_EXTRA) * (1f + SELECTED_EXTRA) - 24.dp - IndicatorInset * 2 - IndicatorPadding * 2)
            .coerceAtLeast(LabelGap)
        Layout(
            content = {
                // La marca va primero: queda detrás de los íconos.
                Box(Modifier.clip(CircleShape).background(indicator))
                tabs.forEachIndexed { index, tab ->
                    PillTab(
                        tab,
                        isSelected = tab == selected,
                        enabled = tab != NavTab.MAPA || mapEnabled,
                        reveal = { reveal(index) },
                        labelMax = labelMax,
                        selectedContent = selectedContent,
                        otherContent = otherContent,
                        onSelect = onSelect,
                        icon = icon,
                    )
                }
            },
            modifier = Modifier.fillMaxSize().selectableGroup(),
        ) { measurables, constraints ->
            val width = constraints.maxWidth
            val height = constraints.maxHeight
            val unit = width / (count + SELECTED_EXTRA)
            val widths = FloatArray(count) { unit * (1f + SELECTED_EXTRA * reveal(it)) }
            val lefts = FloatArray(count)
            var x = 0f
            for (index in 0 until count) {
                lefts[index] = x
                x += widths[index]
            }
            val tabPlaceables = measurables.drop(1).mapIndexed { index, measurable ->
                // Bordes redondeados por separado: las pestañas quedan pegadas, sin huecos de un píxel.
                val tabWidth = (lefts[index] + widths[index]).roundToInt() - lefts[index].roundToInt()
                measurable.measure(Constraints.fixed(tabWidth.coerceAtLeast(0), height))
            }
            // La marca va entre la pestaña de la que sale y la siguiente, según la fracción.
            val at = position.value.coerceIn(0f, count - 1f)
            val from = at.toInt().coerceAtMost(count - 1)
            val to = (from + 1).coerceAtMost(count - 1)
            val fraction = at - from
            val markLeft = lefts[from] + fraction * (lefts[to] - lefts[from])
            val markWidth = widths[from] + fraction * (widths[to] - widths[from])
            val inset = IndicatorInset.roundToPx()
            val markPlaceable = measurables[0].measure(
                Constraints.fixed(
                    (markWidth.roundToInt() - inset * 2).coerceAtLeast(0),
                    IndicatorHeight.roundToPx().coerceAtMost(height),
                ),
            )
            layout(width, height) {
                markPlaceable.placeRelative(markLeft.roundToInt() + inset, (height - markPlaceable.height) / 2)
                tabPlaceables.forEachIndexed { index, placeable -> placeable.placeRelative(lefts[index].roundToInt(), 0) }
            }
        }
    }
}

/** Una pestaña de la píldora: el ícono y, si está elegida, su nombre al lado. Toda la pestaña se toca. */
@Composable
private fun PillTab(
    tab: NavTab,
    isSelected: Boolean,
    enabled: Boolean,
    reveal: () -> Float,
    labelMax: Dp,
    selectedContent: Color,
    otherContent: Color,
    onSelect: (NavTab) -> Unit,
    icon: @Composable (NavTab, Color, Boolean) -> Unit,
) {
    val alpha by animateFloatAsState(if (enabled) 1f else 0.4f, label = "pestaña")
    val tint by animateColorAsState(if (isSelected) selectedContent else otherContent, label = "pestaña color")
    Box(
        Modifier
            .clip(CircleShape)
            .selectable(selected = isSelected, enabled = enabled, role = Role.Tab, onClick = { onSelect(tab) })
            // Sin el nombre a la vista, TalkBack igual dice cuál es.
            .semantics { contentDescription = tab.label }
            .graphicsLayer { this.alpha = alpha },
        contentAlignment = Alignment.Center,
    ) {
        Row(Modifier.clearAndSetSemantics { }, verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) { icon(tab, tint, isSelected) }
            Text(
                tab.label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = tint,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .graphicsLayer { this.alpha = reveal() }
                    .clipToBounds()
                    // El nombre se mide entero y se muestra la parte que toca: crece de a poco
                    // desde el ícono. Se lee al medir: la animación no recompone.
                    .layout { measurable, _ ->
                        val placeable = measurable.measure(Constraints(maxWidth = labelMax.roundToPx()))
                        val shown = (placeable.width * reveal()).roundToInt()
                        layout(shown, placeable.height) { placeable.placeRelative(0, 0) }
                    }
                    .padding(start = LabelGap),
            )
        }
    }
}

/** Las pestañas del riel, una debajo de otra; la marca baja o sube detrás de ícono y nombre. */
@Composable
private fun RailTabs(
    selected: NavTab,
    position: State<Float>,
    mapEnabled: Boolean,
    selectedContent: Color,
    otherContent: Color,
    indicator: Color,
    onSelect: (NavTab) -> Unit,
    icon: @Composable (NavTab, Color, Boolean) -> Unit,
) {
    val tabs = NavTab.entries
    Layout(
        content = {
            Box(Modifier.clip(RoundedCornerShape(20.dp)).background(indicator))
            tabs.forEach { tab ->
                RailTab(
                    tab,
                    isSelected = tab == selected,
                    enabled = tab != NavTab.MAPA || mapEnabled,
                    selectedContent = selectedContent,
                    otherContent = otherContent,
                    onSelect = onSelect,
                    icon = icon,
                )
            }
        },
        modifier = Modifier.fillMaxWidth().selectableGroup(),
    ) { measurables, constraints ->
        val width = constraints.maxWidth
        val pad = RailPadding.roundToPx()
        val item = RailItemHeight.roundToPx()
        val gap = RailItemGap.roundToPx()
        val height = pad * 2 + item * tabs.size + gap * (tabs.size - 1)
        val itemPlaceables = measurables.drop(1).map { it.measure(Constraints.fixed(width, item)) }
        val inset = 6.dp.roundToPx()
        val markPlaceable = measurables[0].measure(Constraints.fixed((width - inset * 2).coerceAtLeast(0), item))
        val at = position.value.coerceIn(0f, tabs.size - 1f)
        layout(width, height) {
            markPlaceable.placeRelative(inset, pad + (at * (item + gap)).roundToInt())
            itemPlaceables.forEachIndexed { index, placeable -> placeable.placeRelative(0, pad + index * (item + gap)) }
        }
    }
}

/** Una pestaña del riel: el ícono y su nombre debajo. */
@Composable
private fun RailTab(
    tab: NavTab,
    isSelected: Boolean,
    enabled: Boolean,
    selectedContent: Color,
    otherContent: Color,
    onSelect: (NavTab) -> Unit,
    icon: @Composable (NavTab, Color, Boolean) -> Unit,
) {
    val alpha by animateFloatAsState(if (enabled) 1f else 0.4f, label = "pestaña")
    val tint by animateColorAsState(if (isSelected) selectedContent else otherContent, label = "pestaña color")
    Box(
        Modifier
            .clip(RoundedCornerShape(20.dp))
            .selectable(selected = isSelected, enabled = enabled, role = Role.Tab, onClick = { onSelect(tab) })
            .graphicsLayer { this.alpha = alpha },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) { icon(tab, tint, isSelected) }
            Text(
                tab.label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                color = tint,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
            )
        }
    }
}

val NavTab.label: String
    get() = when (this) {
        NavTab.CLIMA -> tr("Clima", "Weather")
        NavTab.MAPA -> tr("Mapa", "Map")
        NavTab.CIUDADES -> tr("Ciudades", "Cities")
        NavTab.AJUSTES -> tr("Ajustes", "Settings")
    }

// Íconos propios de las pestañas (solo hay material-icons-core). Contorno con trazo de 2, como
// los de Material, y lleno para la elegida.
private fun tabVector(name: String, build: ImageVector.Builder.() -> Unit): ImageVector =
    ImageVector.Builder(name = name, defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
        .apply(build)
        .build()

// Pares par-impar: lo que queda adentro de otra figura (ventanas) es un hueco, sin depender del sentido del trazo.
private fun ImageVector.Builder.fillPath(path: String) {
    addPath(pathData = addPathNodes(path), pathFillType = PathFillType.EvenOdd, fill = SolidColor(Color.Black))
}

private fun ImageVector.Builder.strokePath(path: String) {
    addPath(
        pathData = addPathNodes(path),
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
    )
}

/** Mapa plegado en tres paños, de contorno. */
internal val MapTabIcon: ImageVector by lazy {
    tabVector("Clima.MapOutlined") {
        strokePath("M3 6L9 4L15 6L21 4V18L15 20L9 18L3 20Z")
        strokePath("M9 4V18")
        strokePath("M15 6V20")
    }
}

/** Mapa plegado lleno: los tres paños separados por los pliegues. */
internal val MapTabIconFilled: ImageVector by lazy {
    tabVector("Clima.MapFilled") {
        fillPath("M3 6L8.4 4.2V18.2L3 20Z")
        fillPath("M9.6 4.2L14.4 5.8V19.8L9.6 18.2Z")
        fillPath("M15.6 5.8L21 4V18L15.6 19.8Z")
    }
}

/** Ciudad: un edificio alto y uno bajo sobre el suelo, de contorno. */
internal val CitiesTabIcon: ImageVector by lazy {
    tabVector("Clima.CityOutlined") {
        strokePath("M5 20V6L11 4V20")
        strokePath("M11 10H19V20")
        strokePath("M3 20H21")
        fillPath("M7 8H9V10H7Z M7 12H9V14H7Z M7 16H9V18H7Z M14 13H16V15H14Z M14 16H16V18H14Z")
    }
}

/** Ciudad llena: los edificios macizos con las ventanas caladas. */
internal val CitiesTabIconFilled: ImageVector by lazy {
    tabVector("Clima.CityFilled") {
        fillPath(
            "M4 21V5.5L12 3V21Z" +
                "M6.5 8H9.5V10H6.5Z M6.5 12H9.5V14H6.5Z M6.5 16H9.5V18H6.5Z",
        )
        fillPath("M13 9H20V21H13Z M15 12H18V14H15Z M15 16H18V18H15Z")
    }
}
