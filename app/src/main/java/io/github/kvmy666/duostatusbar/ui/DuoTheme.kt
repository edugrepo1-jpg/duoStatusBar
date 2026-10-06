package io.github.kvmy666.duostatusbar.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal val StudioBlue = Color(0xFF007AFF)
internal val StudioMint = Color(0xFF30D158)
internal val StudioInk = Color(0xFF111113)
internal val StudioLight = lightColorScheme(
    primary=StudioBlue,onPrimary=Color.White,
    primaryContainer=Color(0xFFE8EEFF),onPrimaryContainer=Color(0xFF214BC0),
    secondary=Color(0xFF7762D6),onSecondary=Color.White,
    secondaryContainer=Color(0xFFEFEBFF),onSecondaryContainer=Color(0xFF5844AF),
    tertiary=Color(0xFF007A59),onTertiary=Color.White,
    tertiaryContainer=Color(0xFFD9F5E8),onTertiaryContainer=Color(0xFF00563F),
    background=Color(0xFFF2F2F7),onBackground=Color(0xFF1C1C1E),
    surface=Color.White,onSurface=Color(0xFF1C1C1E),
    surfaceVariant=Color(0xFFE5E5EA),onSurfaceVariant=Color(0xFF636366),
    outline=Color(0xFF8A94A8),outlineVariant=Color(0xFFE4E8F0),
    error=Color(0xFFBC3547),errorContainer=Color(0xFFFFE9ED),onErrorContainer=Color(0xFF86243A)
)
internal val StudioDark = darkColorScheme(
    primary=Color(0xFF0A84FF),onPrimary=Color(0xFF12295C),
    primaryContainer=Color(0xFF162A45),onPrimaryContainer=Color(0xFF87BBFF),
    secondary=Color(0xFFBCAEFA),onSecondary=Color(0xFF302353),
    secondaryContainer=Color(0xFF332C4A),onSecondaryContainer=Color(0xFFDAD0FF),
    tertiary=StudioMint,onTertiary=Color(0xFF003B2B),
    tertiaryContainer=Color(0xFF183E32),onTertiaryContainer=Color(0xFFA0EFCE),
    background=Color(0xFF000000),onBackground=Color(0xFFF0F3FA),
    surface=Color(0xFF1C1C1E),onSurface=Color(0xFFF0F3FA),
    surfaceVariant=Color(0xFF2C2C2E),onSurfaceVariant=Color(0xFFAEAEB2),
    outline=Color(0xFF718098),outlineVariant=Color(0xFF38383A),
    error=Color(0xFFFF9CAA),errorContainer=Color(0xFF492533),onErrorContainer=Color(0xFFFFCDD5)
)
private val StudioType = Typography(
    headlineLarge=TextStyle(fontFamily=FontFamily.SansSerif,fontWeight=FontWeight.Bold,fontSize=32.sp,lineHeight=38.sp,letterSpacing=(-1.2).sp),
    headlineSmall=TextStyle(fontFamily=FontFamily.SansSerif,fontWeight=FontWeight.Bold,fontSize=25.sp,lineHeight=30.sp,letterSpacing=(-.6).sp),
    titleLarge=TextStyle(fontFamily=FontFamily.SansSerif,fontWeight=FontWeight.SemiBold,fontSize=21.sp,lineHeight=27.sp),
    titleMedium=TextStyle(fontFamily=FontFamily.SansSerif,fontWeight=FontWeight.SemiBold,fontSize=18.sp,lineHeight=24.sp,letterSpacing=(-.3).sp),
    titleSmall=TextStyle(fontFamily=FontFamily.SansSerif,fontWeight=FontWeight.SemiBold,fontSize=16.sp,lineHeight=22.sp),
    bodyLarge=TextStyle(fontFamily=FontFamily.SansSerif,fontWeight=FontWeight.Medium,fontSize=16.sp,lineHeight=22.sp),
    bodyMedium=TextStyle(fontFamily=FontFamily.SansSerif,fontSize=14.sp,lineHeight=20.sp),
    bodySmall=TextStyle(fontFamily=FontFamily.SansSerif,fontSize=12.sp,lineHeight=18.sp),
    labelLarge=TextStyle(fontFamily=FontFamily.SansSerif,fontWeight=FontWeight.SemiBold,fontSize=13.sp,lineHeight=18.sp),
    labelSmall=TextStyle(fontFamily=FontFamily.SansSerif,fontWeight=FontWeight.SemiBold,fontSize=11.sp,lineHeight=15.sp,letterSpacing=.4.sp)
)
@Composable
fun DuoTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(colorScheme=if(dark) StudioDark else StudioLight,typography=StudioType,
        shapes=Shapes(small=RoundedCornerShape(12.dp),medium=RoundedCornerShape(20.dp),large=RoundedCornerShape(28.dp)),content=content)
}
