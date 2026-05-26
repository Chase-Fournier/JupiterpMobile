package com.jupiterp.jupiterpmobile.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.jupiterp.jupiterpmobile.domain.model.ClassMeeting
import com.jupiterp.jupiterpmobile.domain.model.Section
import com.jupiterp.jupiterpmobile.toOneDecimalString
import com.jupiterp.ui.theme.JupiterpColors
import com.jupiterp.ui.theme.JupiterpTheme
import kotlin.math.cos
import kotlin.math.sin

/**
 * GenEd Badge - Orange accent for GenEd codes
 */
@Composable
fun GenEdBadge(
    genEd: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    Surface(
        modifier = modifier
            .then(
                if (onClick != null) {
                    Modifier.clickable(onClick = onClick)
                } else {
                    Modifier
                }
            ),
        shape = RoundedCornerShape(6.dp),
        color = JupiterpTheme.extendedColors.orangeContainer,
        contentColor = JupiterpTheme.extendedColors.orange
    ) {
        Text(
            text = genEd,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold
        )
    }
}

/**
 * Credits Badge
 */
@Composable
fun CreditsBadge(
    credits: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
    ) {
        Text(
            text = "$credits cr",
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium
        )
    }
}

/**
 * Instructor Rating Chip
 */
@Composable
fun RatingChip(
    rating: Float?,
    modifier: Modifier = Modifier
) {
    if (rating == null) return

    val color = JupiterpColors.ratingColor(rating)

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(6.dp),
        color = color.copy(alpha = 0.15f),
        contentColor = color
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Star,
                contentDescription = null,
                modifier = Modifier.size(12.dp)
            )
            Text(
                text = rating.toOneDecimalString(),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}
/**
 * Seats Badge with color coding
 */
@Composable
fun SeatsBadge(
    openSeats: Int,
    totalSeats: Int,
    modifier: Modifier = Modifier
) {
    val color = JupiterpColors.seatColor(openSeats, totalSeats)

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(6.dp),
        color = color.copy(alpha = 0.15f),
        contentColor = color
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Icon(
                imageVector = if (openSeats > 0) Icons.Outlined.EventSeat else Icons.Filled.Block,
                contentDescription = null,
                modifier = Modifier.size(12.dp)
            )
            Text(
                text = "$openSeats/$totalSeats",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

/**
 * Filter Chip for department/genEd selection
 */
@Composable
fun FilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null
) {
    val containerColor = if (selected) {
        JupiterpTheme.extendedColors.orange
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }

    val contentColor = if (selected) {
        Color.White
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(10.dp), // Slightly larger squircle
        color = containerColor,
        contentColor = contentColor
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), // Slightly larger padding
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            if (leadingIcon != null) {
                Icon(
                    imageVector = leadingIcon,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp) // Slightly larger icon
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium, // Slightly larger text
                fontWeight = FontWeight.Medium
            )
            if (selected) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Remove filter",
                    modifier = Modifier.size(12.dp) // Slightly larger close icon
                )
            }
        }
    }
}

/**
 * Section Row Component
 */
@Composable
fun SectionRow(
    section: Section,
    isSelected: Boolean,
    instructorRating: Float?,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    hasConflict: Boolean = false
) {
    val backgroundColor = if (isSelected) {
        JupiterpTheme.extendedColors.orangeContainer
    } else {
        MaterialTheme.colorScheme.surface
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle),
        color = backgroundColor,
        shape = RoundedCornerShape(12.dp),
        border = if (isSelected) {
            null
        } else {
            androidx.compose.foundation.BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant
            )
        }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Section code and instructor
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = section.sectionCode,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = JupiterpTheme.extendedColors.sectionCodes
                    )

                    Text(
                        text = section.instructorsDisplay,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    if (instructorRating != null) {
                        RatingChip(rating = instructorRating)
                    }
                }

                // Meeting times
                section.meetings.forEach { meeting ->
                    MeetingInfo(meeting = meeting)
                }
            }

            // Seats and selection indicator
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SeatsBadge(
                    openSeats = section.openSeats,
                    totalSeats = section.totalSeats
                )

                // Selection indicator
                val indicatorBg = when {
                    isSelected -> JupiterpTheme.extendedColors.orange
                    hasConflict -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.surfaceVariant
                }
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(indicatorBg),
                    contentAlignment = Alignment.Center
                ) {
                    when {
                        isSelected -> Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = "Selected",
                            modifier = Modifier.size(16.dp),
                            tint = Color.White
                        )
                        hasConflict -> Icon(
                            imageVector = Icons.Outlined.Schedule,
                            contentDescription = "Time conflict",
                            modifier = Modifier.size(16.dp),
                            tint = Color.White
                        )
                    }
                }
            }
        }
    }
}

/**
 * Meeting time/location info
 */
@Composable
fun MeetingInfo(
    meeting: ClassMeeting,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        when (meeting) {
            is ClassMeeting.InPerson -> {
                Icon(
                    imageVector = Icons.Outlined.Schedule,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = JupiterpTheme.extendedColors.textSecondary
                )
                Text(
                    text = "${meeting.classtime.days} ${meeting.classtime.timeRange}",
                    style = MaterialTheme.typography.bodySmall,
                    color = JupiterpTheme.extendedColors.textSecondary
                )
                Icon(
                    imageVector = Icons.Outlined.LocationOn,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = JupiterpTheme.extendedColors.textSecondary
                )
                Text(
                    text = meeting.location.display,
                    style = MaterialTheme.typography.bodySmall,
                    color = JupiterpTheme.extendedColors.textSecondary
                )
            }
            is ClassMeeting.OnlineSync -> {
                Icon(
                    imageVector = Icons.Outlined.Videocam,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = JupiterpTheme.extendedColors.textSecondary
                )
                Text(
                    text = "Online Sync - ${meeting.classtime.days} ${meeting.classtime.timeRange}",
                    style = MaterialTheme.typography.bodySmall,
                    color = JupiterpTheme.extendedColors.textSecondary
                )
            }
            is ClassMeeting.OnlineAsync -> {
                Icon(
                    imageVector = Icons.Outlined.CloudQueue,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = JupiterpTheme.extendedColors.textSecondary
                )
                Text(
                    text = "Online Asynchronous",
                    style = MaterialTheme.typography.bodySmall,
                    color = JupiterpTheme.extendedColors.textSecondary
                )
            }
            is ClassMeeting.TBA -> {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.HelpOutline,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = JupiterpTheme.extendedColors.textSecondary
                )
                Text(
                    text = "TBA",
                    style = MaterialTheme.typography.bodySmall,
                    color = JupiterpTheme.extendedColors.textSecondary
                )
            }
            is ClassMeeting.Unknown -> {
                Text(
                    text = "Unknown",
                    style = MaterialTheme.typography.bodySmall,
                    color = JupiterpTheme.extendedColors.textSecondary
                )
            }
        }
    }
}

/**
 * Empty state component
 */
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = JupiterpTheme.extendedColors.textSecondary.copy(alpha = 0.5f)
        )

        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )

        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = JupiterpTheme.extendedColors.textSecondary
        )

        action?.invoke()
    }
}

/**
 * Loading indicator with branded animation
 */
@Composable
fun LoadingIndicator(
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "loading")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(40.dp),
            color = JupiterpTheme.extendedColors.orange,
            strokeWidth = 3.dp
        )
    }
}

/**
 * Action button with orange accent
 */
@Composable
fun JupiterpButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    variant: ButtonVariant = ButtonVariant.Filled
) {
    val colors = when (variant) {
        ButtonVariant.Filled -> ButtonDefaults.buttonColors(
            containerColor = JupiterpTheme.extendedColors.orange,
            contentColor = Color.White
        )
        ButtonVariant.Outlined -> ButtonDefaults.outlinedButtonColors(
            contentColor = JupiterpTheme.extendedColors.orange
        )
        ButtonVariant.Text -> ButtonDefaults.textButtonColors(
            contentColor = JupiterpTheme.extendedColors.orange
        )
    }

    when (variant) {
        ButtonVariant.Filled -> {
            Button(
                onClick = onClick,
                modifier = modifier,
                enabled = enabled,
                colors = colors,
                shape = RoundedCornerShape(12.dp)
            ) {
                ButtonContent(text = text, icon = icon)
            }
        }
        ButtonVariant.Outlined -> {
            OutlinedButton(
                onClick = onClick,
                modifier = modifier,
                enabled = enabled,
                colors = colors,
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (enabled) JupiterpTheme.extendedColors.orange else MaterialTheme.colorScheme.outline
                )
            ) {
                ButtonContent(text = text, icon = icon)
            }
        }
        ButtonVariant.Text -> {
            TextButton(
                onClick = onClick,
                modifier = modifier,
                enabled = enabled,
                colors = colors
            ) {
                ButtonContent(text = text, icon = icon)
            }
        }
    }
}

@Composable
private fun ButtonContent(text: String, icon: ImageVector?) {
    if (icon != null) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
    }
    Text(
        text = text,
        fontWeight = FontWeight.SemiBold
    )
}

enum class ButtonVariant {
    Filled, Outlined, Text
}

/**
 * Solar System themed loading animation
 * Based on Marvin Rudolph's Orbit Loader, adapted for Compose
 */
@Composable
fun SolarSystemLoader(
    modifier: Modifier = Modifier,
    size: Dp = 120.dp,
    color: Color = JupiterpTheme.extendedColors.orange
) {
    // Animation values
    val infiniteTransition = rememberInfiniteTransition(label = "solar")

    // Fade in/out animation
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 4000
                0f at 0 using LinearEasing
                1f at 600 using LinearEasing
                1f at 3400 using LinearEasing
                0f at 4000 using LinearEasing
            }
        ),
        label = "alpha"
    )

    // Scale animation
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 4000
                0.8f at 0 using FastOutSlowInEasing
                1f at 600 using FastOutSlowInEasing
                1f at 3400 using FastOutSlowInEasing
                1.1f at 4000 using FastOutSlowInEasing
            }
        ),
        label = "scale"
    )

    // Inner ring rotation (faster)
    val innerRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(8000, easing = LinearEasing)
        ),
        label = "innerRotation"
    )

    // Outer ring rotation (slower)
    val outerRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(14000, easing = LinearEasing)
        ),
        label = "outerRotation"
    )

    // Sizing calculations
    val moonSize = size * 0.30f
    val innerRingSize = size * 0.50f
    val outerRingSize = size * 0.69f
    val planetSize = 10.dp
    val orbitWidth = 2.dp
    val moonBorderWidth = 3.dp

    Box(
        modifier = modifier
            .size(size * scale)
            .graphicsLayer { this.alpha = alpha },
        contentAlignment = Alignment.Center
    ) {
        Canvas(
            modifier = Modifier.fillMaxSize()
        ) {
            val center = Offset(this.size.width / 2f, this.size.height / 2f)
            val moonRadius = moonSize.toPx() / 2f
            val innerRingRadius = innerRingSize.toPx() / 2f
            val outerRingRadius = outerRingSize.toPx() / 2f
            val planetRadius = planetSize.toPx() / 2f

            // Draw outer ring (orbit path)
            drawCircle(
                color = color,
                radius = outerRingRadius,
                center = center,
                style = Stroke(width = orbitWidth.toPx())
            )

            // Draw inner ring (orbit path)
            drawCircle(
                color = color,
                radius = innerRingRadius,
                center = center,
                style = Stroke(width = orbitWidth.toPx())
            )

            // Draw moon (center circle with border)
            drawCircle(
                color = color,
                radius = moonRadius,
                center = center,
                style = Stroke(width = moonBorderWidth.toPx())
            )

            // Draw moon craters
            val craterColor = color
            // Crater 1 - larger, top-left
            drawCircle(
                color = craterColor,
                radius = moonRadius * 0.20f,
                center = Offset(
                    center.x - moonRadius * 0.25f,
                    center.y - moonRadius * 0.25f
                )
            )
            // Crater 2 - medium, bottom-right
            drawCircle(
                color = craterColor,
                radius = moonRadius * 0.12f,
                center = Offset(
                    center.x + moonRadius * 0.30f,
                    center.y + moonRadius * 0.30f
                )
            )
            // Crater 3 - small, right
            drawCircle(
                color = craterColor,
                radius = moonRadius * 0.08f,
                center = Offset(
                    center.x + moonRadius * 0.35f,
                    center.y - moonRadius * 0.10f
                )
            )

            // Draw inner planet (rotating)
            val innerAngle = innerRotation * kotlin.math.PI.toFloat() / 180f
            val innerPlanetX = center.x + (innerRingRadius * cos(innerAngle))
            val innerPlanetY = center.y + (innerRingRadius * sin(innerAngle))
            drawCircle(
                color = color,
                radius = planetRadius,
                center = Offset(innerPlanetX, innerPlanetY)
            )

            // Draw outer planet (rotating)
            val outerAngle = outerRotation * kotlin.math.PI.toFloat() / 180f
            val outerPlanetX = center.x + (outerRingRadius * cos(outerAngle))
            val outerPlanetY = center.y + (outerRingRadius * sin(outerAngle))
            drawCircle(
                color = color,
                radius = planetRadius,
                center = Offset(outerPlanetX, outerPlanetY)
            )
        }
    }
}