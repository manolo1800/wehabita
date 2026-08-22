package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*

@Composable
fun WebHabitaLogo(
    modifier: Modifier = Modifier,
    size: Dp = 100.dp,
    showCardContainer: Boolean = true,
    showText: Boolean = true
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        val containerModifier = if (showCardContainer) {
            Modifier
                .size(size)
                .clip(RoundedCornerShape(size * 0.22f))
                .background(SandSurface)
                .border(1.dp, SandOutline.copy(alpha = 0.5f), RoundedCornerShape(size * 0.22f))
                .padding(size * 0.10f)
        } else {
            Modifier.size(size)
        }

        Box(
            modifier = containerModifier,
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = this.size.width
                val h = this.size.height

                val scaleX = w / 200f
                val scaleY = h / 200f

                fun sx(x: Float) = x * scaleX
                fun sy(y: Float) = y * scaleY

                // 1. Terracotta Chimney on Right Slope
                drawRoundRect(
                    color = TerracottaSecondary,
                    topLeft = Offset(sx(124f), sy(48f)),
                    size = Size(sx(10f), sy(22f)),
                    cornerRadius = CornerRadius(sx(2f), sy(2f)),
                    style = Fill
                )

                // 2. Terracotta 2x2 Window Panes
                val paneSize = sx(7.5f)
                val gap = sx(5.5f)
                val leftX = sx(91f)
                val topY = sy(70f)

                // Top-Left
                drawRoundRect(
                    color = TerracottaSecondary,
                    topLeft = Offset(leftX, topY),
                    size = Size(paneSize, paneSize),
                    cornerRadius = CornerRadius(sx(1.5f), sy(1.5f)),
                    style = Fill
                )
                // Top-Right
                drawRoundRect(
                    color = TerracottaSecondary,
                    topLeft = Offset(leftX + paneSize + gap, topY),
                    size = Size(paneSize, paneSize),
                    cornerRadius = CornerRadius(sx(1.5f), sy(1.5f)),
                    style = Fill
                )
                // Bottom-Left
                drawRoundRect(
                    color = TerracottaSecondary,
                    topLeft = Offset(leftX, topY + paneSize + gap),
                    size = Size(paneSize, paneSize),
                    cornerRadius = CornerRadius(sx(1.5f), sy(1.5f)),
                    style = Fill
                )
                // Bottom-Right
                drawRoundRect(
                    color = TerracottaSecondary,
                    topLeft = Offset(leftX + paneSize + gap, topY + paneSize + gap),
                    size = Size(paneSize, paneSize),
                    cornerRadius = CornerRadius(sx(1.5f), sy(1.5f)),
                    style = Fill
                )

                // 3. Sage Green Roofline
                val roofPath = Path().apply {
                    moveTo(sx(52f), sy(78f))
                    lineTo(sx(100f), sy(46f))
                    lineTo(sx(150f), sy(80f))
                }
                drawPath(
                    path = roofPath,
                    color = SageGreenPrimary,
                    style = Stroke(
                        width = sx(9f),
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )

                // 4. Sage Green 'w' (Left Half)
                val wPath = Path().apply {
                    moveTo(sx(58f), sy(88f))
                    lineTo(sx(58f), sy(112f))
                    cubicTo(sx(58f), sy(124f), sx(68f), sy(127f), sx(76f), sy(127f))
                    cubicTo(sx(84f), sy(127f), sx(90f), sy(122f), sx(90f), sy(112f))
                    lineTo(sx(90f), sy(97f))

                    moveTo(sx(90f), sy(112f))
                    cubicTo(sx(90f), sy(122f), sx(96f), sy(127f), sx(104f), sy(127f))
                    cubicTo(sx(112f), sy(127f), sx(118f), sy(122f), sx(118f), sy(112f))
                    lineTo(sx(118f), sy(97f))
                }
                drawPath(
                    path = wPath,
                    color = SageGreenPrimary,
                    style = Stroke(
                        width = sx(9f),
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )

                // 5. Earthy Brown 'h' (Right Half)
                // Vertical stem extending up to roof
                drawRoundRect(
                    color = EarthyMediumGray,
                    topLeft = Offset(sx(111f), sy(54f)),
                    size = Size(sx(9f), sy(74f)),
                    cornerRadius = CornerRadius(sx(3f), sy(3f)),
                    style = Fill
                )
                // Arch of the 'h'
                val hArchPath = Path().apply {
                    moveTo(sx(115f), sy(95f))
                    cubicTo(sx(124f), sy(86f), sx(138f), sy(86f), sx(146f), sy(96f))
                    cubicTo(sx(151f), sy(102f), sx(152f), sy(110f), sx(152f), sy(128f))
                }
                drawPath(
                    path = hArchPath,
                    color = EarthyMediumGray,
                    style = Stroke(
                        width = sx(9f),
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )

                // 6. Terracotta Accent Line below
                drawLine(
                    color = TerracottaSecondary,
                    start = Offset(sx(94f), sy(164f)),
                    end = Offset(sx(114f), sy(164f)),
                    strokeWidth = sx(3.5f),
                    cap = StrokeCap.Round
                )
            }
        }

        if (showText) {
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "webhabita",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontFamily = FontFamily.SansSerif,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 3.sp
                ),
                color = EarthyDarkText
            )
            Box(
                modifier = Modifier
                    .padding(top = 4.dp)
                    .width(36.dp)
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(TerracottaSecondary)
            )
        }
    }
}
