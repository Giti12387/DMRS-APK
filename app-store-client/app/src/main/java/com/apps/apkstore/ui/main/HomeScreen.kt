package com.apps.apkstore.ui.main

import android.content.Intent
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.apps.apkstore.data.model.AppModel
import com.apps.apkstore.data.model.CategoryModel
import com.apps.apkstore.data.model.DownloadStatus
import com.apps.apkstore.data.repository.AppRepository
import com.apps.apkstore.service.DownloadService
import com.apps.apkstore.ui.components.AppIconWithProgress
import com.apps.apkstore.ui.components.AppIconWithProgressSmall
import com.apps.apkstore.ui.theme.*
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.schedulers.Schedulers

@Composable
fun HomeScreen(viewModel: MainViewModel, onAppClick: (AppModel) -> Unit, onCategoryClick: (String) -> Unit, onSearchClick: () -> Unit) {
    val homeData by viewModel.homeData
    val isLoading by viewModel.isLoading
    val error by viewModel.error

    LaunchedEffect(Unit) { viewModel.loadHomeData() }

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).verticalScroll(rememberScrollState())) {
        Column(modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).statusBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column { Text("APK Store", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface); Text("Discover amazing apps", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Card(modifier = Modifier.fillMaxWidth().clickable { onSearchClick() }, shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Search, "Search", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("Search apps & games", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f), fontSize = 15.sp)
                }
            }
        }

        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize().padding(50.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Primary) }
        } else if (error != null) {
            Box(modifier = Modifier.fillMaxSize().padding(50.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Outlined.CloudOff, "Error", tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f), modifier = Modifier.size(48.dp))
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(error ?: "", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(onClick = { viewModel.loadHomeData() }, colors = ButtonDefaults.buttonColors(containerColor = Primary)) { Text("Retry") }
                }
            }
        } else {
            homeData?.featuredApps?.firstOrNull()?.let { featured ->
                FeaturedBanner(app = featured, onClick = { onAppClick(featured) })
            }
            Spacer(modifier = Modifier.height(20.dp))
            homeData?.categories?.let { categories ->
                if (categories.isNotEmpty()) {
                    SectionHeader(title = "Categories", seeAll = { })
                    LazyRow(modifier = Modifier.padding(start = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(categories) { category -> CategoryChip(category = category, onClick = { onCategoryClick(category.name) }) }
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
            homeData?.newReleases?.let { apps ->
                if (apps.isNotEmpty()) {
                    SectionHeader(title = "New & Updated", seeAll = { })
                    LazyRow(modifier = Modifier.padding(start = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(apps) { app -> AppCardHorizontal(app = app, onClick = { onAppClick(app) }) }
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
            homeData?.topCharts?.let { apps ->
                if (apps.isNotEmpty()) {
                    SectionHeader(title = "Top Charts", seeAll = { })
                    apps.take(10).forEachIndexed { index, app -> AppListItem(app = app, rank = index + 1, onClick = { onAppClick(app) }) }
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
            homeData?.recommendedApps?.let { apps ->
                if (apps.isNotEmpty()) {
                    SectionHeader(title = "Recommended for You", seeAll = { })
                    LazyRow(modifier = Modifier.padding(start = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(apps) { app -> AppCardVertical(app = app, onClick = { onAppClick(app) }) }
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
fun FeaturedBanner(app: AppModel, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().height(180.dp).padding(16.dp).clickable { onClick() }, shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color.Transparent)) {
        Box(modifier = Modifier.fillMaxSize()) {
            AsyncImage(model = ImageRequest.Builder(LocalContext.current).data(app.bannerUrl.ifBlank { app.iconUrl }).crossfade(true).build(), contentDescription = app.name, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            Box(modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f)))))
            Column(modifier = Modifier.align(Alignment.BottomStart).padding(16.dp)) {
                Text("Featured", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Secondary, letterSpacing = 1.sp)
                Spacer(modifier = Modifier.height(4.dp))
                Text(app.name, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Text(app.shortDescription.ifBlank { app.developerName }, fontSize = 13.sp, color = Color.White.copy(alpha = 0.8f), maxLines = 1)
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Star, "Rating", tint = RatingStar, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(String.format("%.1f", app.rating), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                    Text("  •  ", fontSize = 12.sp, color = Color.White.copy(alpha = 0.5f))
                    Text(app.category, fontSize = 12.sp, color = Color.White.copy(alpha = 0.7f))
                }
            }
        }
    }
}

@Composable
fun SectionHeader(title: String, seeAll: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
        TextButton(onClick = seeAll) { Text("See All", fontSize = 13.sp, color = Primary) }
    }
}

@Composable
fun CategoryChip(category: CategoryModel, onClick: () -> Unit) {
    Card(modifier = Modifier.clickable { onClick() }.width(100.dp), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(modifier = Modifier.size(40.dp).clip(CircleShape).background(Primary.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                Icon(getCategoryIcon(category.name), contentDescription = category.name, tint = Primary, modifier = Modifier.size(20.dp))
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(category.name, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
fun AppCardHorizontal(app: AppModel, onClick: () -> Unit) {
    Card(modifier = Modifier.width(150.dp).clickable { onClick() }, shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column {
            AsyncImage(model = ImageRequest.Builder(LocalContext.current).data(app.iconUrl).crossfade(true).build(), contentDescription = app.name, modifier = Modifier.fillMaxWidth().height(140.dp).clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)), contentScale = ContentScale.Crop)
            Column(modifier = Modifier.padding(10.dp)) {
                Text(app.name, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(modifier = Modifier.height(2.dp))
                Text(app.developerName, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(modifier = Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Star, "Rating", tint = RatingStar, modifier = Modifier.size(12.dp))
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(String.format("%.1f", app.rating), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
fun AppCardVertical(app: AppModel, onClick: () -> Unit) {
    Card(modifier = Modifier.width(140.dp).clickable { onClick() }, shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            AppIconWithProgress(
                app = app,
                iconSize = 64.dp,
                showProgress = true
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(app.name, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 14.sp)
            Spacer(modifier = Modifier.height(4.dp))
            Text(app.developerName, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(modifier = Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Star, "Rating", tint = RatingStar, modifier = Modifier.size(10.dp))
                Spacer(modifier = Modifier.width(3.dp))
                Text(String.format("%.1f", app.rating), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun AppListItem(app: AppModel, rank: Int, onClick: () -> Unit) {
    val context = LocalContext.current
    var downloadState by remember { mutableStateOf(app.downloadStatus) }

    Row(modifier = Modifier.fillMaxWidth().clickable { onClick() }.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("$rank", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f), modifier = Modifier.width(28.dp))
        AppIconWithProgressSmall(
            app = app,
            iconSize = 52.dp
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(app.name, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(modifier = Modifier.height(2.dp))
            Text(app.developerName, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            Spacer(modifier = Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Star, "Rating", tint = RatingStar, modifier = Modifier.size(12.dp))
                Spacer(modifier = Modifier.width(3.dp))
                Text(String.format("%.1f", app.rating), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("  •  ", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                Text(app.category, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
            }
        }
        InstallButton(app = app, downloadState = downloadState, onStateChange = { downloadState = it })
    }
}

@Composable
fun InstallButton(app: AppModel, downloadState: DownloadStatus, onStateChange: (DownloadStatus) -> Unit) {
    val context = LocalContext.current
    val repo = remember { context.applicationContext as com.apps.apkstore.APKStoreApplication }

    val actuallyInstalled = remember(app.packageName) {
        try {
            context.packageManager.getPackageInfo(app.packageName, 0)
            true
        } catch (e: Exception) {
            false
        }
    }

    Button(
        onClick = {
            when {
                actuallyInstalled -> {
                    val intent = context.packageManager.getLaunchIntentForPackage(app.packageName)
                    intent?.let { it.flags = Intent.FLAG_ACTIVITY_NEW_TASK; context.startActivity(it) }
                }
                downloadState == DownloadStatus.NONE || downloadState == DownloadStatus.FAILED -> {
                    onStateChange(DownloadStatus.PENDING)
                    val d = repo.getAppEntryPoint().repository().startDownload(app)
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe({ download ->
                            val intent = Intent(context, DownloadService::class.java).apply {
                                action = DownloadService.ACTION_START
                                putExtra(DownloadService.EXTRA_DOWNLOAD_ID, download.id)
                            }
                            try {
                                context.startForegroundService(intent)
                            } catch (e: Exception) {
                                e.printStackTrace()
                                context.startService(intent)
                            }
                        }, { _ -> onStateChange(DownloadStatus.NONE) })
                }
                downloadState == DownloadStatus.COMPLETED -> {
                    // Install handled by DownloadService autoInstall
                }
            }
        },
        shape = RoundedCornerShape(8.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = when {
                actuallyInstalled -> Secondary
                downloadState == DownloadStatus.DOWNLOADING -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                else -> Primary
            },
            contentColor = Color.White
        ),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
        modifier = Modifier.height(32.dp),
        enabled = downloadState != DownloadStatus.DOWNLOADING && downloadState != DownloadStatus.INSTALLING
    ) {
        when {
            downloadState == DownloadStatus.DOWNLOADING || downloadState == DownloadStatus.PENDING -> {
                CircularProgressIndicator(modifier = Modifier.size(14.dp), color = Color.White, strokeWidth = 2.dp)
                Spacer(modifier = Modifier.width(6.dp))
                Text("...", fontSize = 12.sp)
            }
            actuallyInstalled -> Text("Open", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            downloadState == DownloadStatus.COMPLETED -> Text("Open", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            else -> Text("Install", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

fun getCategoryIcon(category: String): ImageVector = when (category.lowercase()) {
    "games", "gaming" -> Icons.Filled.SportsEsports
    "social", "communication" -> Icons.Filled.Chat
    "tools", "utilities" -> Icons.Filled.Build
    "music", "audio" -> Icons.Filled.MusicNote
    "video", "entertainment" -> Icons.Filled.PlayCircle
    "photo", "camera" -> Icons.Filled.PhotoCamera
    "education" -> Icons.Filled.School
    "business" -> Icons.Filled.Business
    "shopping" -> Icons.Filled.ShoppingBag
    "health" -> Icons.Filled.Favorite
    "travel" -> Icons.Filled.Flight
    "books" -> Icons.Filled.MenuBook
    "finance" -> Icons.Filled.AccountBalance
    "weather" -> Icons.Filled.WbSunny
    "fitness" -> Icons.Filled.FitnessCenter
    else -> Icons.Filled.Apps
}
