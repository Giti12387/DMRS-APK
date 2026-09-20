package com.apps.apkstore.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.apps.apkstore.data.model.AppModel
import com.apps.apkstore.data.model.DownloadStatus
import com.apps.apkstore.ui.theme.Primary

@Composable
fun AppIconWithProgress(
    app: AppModel,
    modifier: Modifier = Modifier,
    iconSize: Dp = 64.dp,
    showProgress: Boolean = true,
    progressColor: Color = Primary,
    backgroundColor: Color = MaterialTheme.colorScheme.surfaceVariant
) {
    val downloadProgress by remember(app.downloadProgress) {
        mutableFloatStateOf(app.downloadProgress)
    }
    
    val isDownloading = app.downloadStatus == DownloadStatus.DOWNLOADING || 
                        app.downloadStatus == DownloadStatus.PENDING ||
                        app.downloadStatus == DownloadStatus.INSTALLING
    
    val animatedProgress by animateFloatAsState(
        targetValue = if (isDownloading) downloadProgress else 0f,
        animationSpec = tween(durationMillis = 300),
        label = "progress"
    )
    
    Box(
        modifier = modifier.size(iconSize + 8.dp),
        contentAlignment = Alignment.Center
    ) {
        if (showProgress && isDownloading && downloadProgress > 0f) {
            // Circular progress background
            CircularProgressIndicator(
                progress = { 1f },
                modifier = Modifier
                    .size(iconSize + 8.dp)
                    .padding(2.dp),
                color = backgroundColor,
                strokeWidth = 3.dp,
                strokeCap = StrokeCap.Round
            )
            
            // Circular progress
            CircularProgressIndicator(
                progress = { animatedProgress / 100f },
                modifier = Modifier
                    .size(iconSize + 8.dp)
                    .padding(2.dp),
                color = progressColor,
                strokeWidth = 3.dp,
                strokeCap = StrokeCap.Round
            )
        }
        
        // App icon
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(app.iconUrl)
                .crossfade(true)
                .build(),
            contentDescription = app.name,
            modifier = Modifier
                .size(iconSize)
                .clip(RoundedCornerShape(iconSize.value * 0.22f)),
            contentScale = ContentScale.Crop
        )
        
        // Progress percentage text (small, at bottom right)
        if (showProgress && isDownloading && downloadProgress > 0f) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
                    .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "${downloadProgress.toInt()}",
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }
    }
}

@Composable
fun AppIconWithProgressSmall(
    app: AppModel,
    modifier: Modifier = Modifier,
    iconSize: Dp = 48.dp
) {
    val downloadProgress by remember(app.downloadProgress) {
        mutableFloatStateOf(app.downloadProgress)
    }
    
    val isDownloading = app.downloadStatus == DownloadStatus.DOWNLOADING || 
                        app.downloadStatus == DownloadStatus.PENDING ||
                        app.downloadStatus == DownloadStatus.INSTALLING
    
    val animatedProgress by animateFloatAsState(
        targetValue = if (isDownloading) downloadProgress else 0f,
        animationSpec = tween(durationMillis = 300),
        label = "progress"
    )
    
    Box(
        modifier = modifier.size(iconSize + 4.dp),
        contentAlignment = Alignment.Center
    ) {
        if (isDownloading && downloadProgress > 0f) {
            CircularProgressIndicator(
                progress = { animatedProgress / 100f },
                modifier = Modifier
                    .size(iconSize + 4.dp)
                    .padding(1.dp),
                color = Primary,
                strokeWidth = 2.dp,
                strokeCap = StrokeCap.Round
            )
        }
        
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(app.iconUrl)
                .crossfade(true)
                .build(),
            contentDescription = app.name,
            modifier = Modifier
                .size(iconSize)
                .clip(RoundedCornerShape(iconSize.value * 0.2f)),
            contentScale = ContentScale.Crop
        )
    }
}
