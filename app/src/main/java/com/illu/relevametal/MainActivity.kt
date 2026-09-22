package com.illu.relevametal

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import com.illu.relevametal.camera.CameraCapture
import com.illu.relevametal.ui.*

class MainActivity : ComponentActivity() {
    private val vm by viewModels<AppViewModel>()


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            GrupoIdeaTheme {
                App(vm = vm)
            }
        }
    }
}

private sealed interface Screen {
    data object Projects : Screen
    data class Project(val projectId: Long) : Screen
    data class Space(val projectId: Long, val spaceId: Long) : Screen
    data class Opening(val projectId: Long, val spaceId: Long, val openingId: Long) : Screen
    data class Camera(val projectId: Long, val spaceId: Long, val openingId: Long) : Screen
    data class Evidence(
        val projectId: Long,
        val spaceId: Long,
        val openingId: Long,
        val evidenceId: Long
    ) : Screen
}

@Composable
private fun App(vm: AppViewModel) {
    val context = LocalContext.current
    var screen by remember { mutableStateOf<Screen>(Screen.Projects) }

    fun goBack() {
        screen = when (val current = screen) {
            Screen.Projects -> Screen.Projects
            is Screen.Project -> Screen.Projects
            is Screen.Space -> Screen.Project(current.projectId)
            is Screen.Opening -> Screen.Space(current.projectId, current.spaceId)
            is Screen.Camera -> Screen.Opening(current.projectId, current.spaceId, current.openingId)
            is Screen.Evidence -> Screen.Opening(current.projectId, current.spaceId, current.openingId)
        }
    }

    BackHandler(enabled = screen !is Screen.Projects) { goBack() }

    when (val current = screen) {
        Screen.Projects -> {
            ProjectsScreen(
                vm = vm,
                onOpen = { projectId -> screen = Screen.Project(projectId) }
            )
        }

        is Screen.Project -> {
            ProjectScreen(
                vm = vm,
                projectId = current.projectId,
                onBack = { screen = Screen.Projects },
                onOpenSpace = { spaceId ->
                    screen = Screen.Space(current.projectId, spaceId)
                },
                onSharePdf = { file ->
                    val uri = FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.files",
                        file
                    )
                    val sendIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "application/pdf"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(
                        Intent.createChooser(sendIntent, "Compartir informe de relevamiento")
                    )
                }
            )
        }

        is Screen.Space -> {
            SpaceScreen(
                vm = vm,
                projectId = current.projectId,
                spaceId = current.spaceId,
                onBack = { screen = Screen.Project(current.projectId) },
                onOpenOpening = { openingId ->
                    screen = Screen.Opening(
                        current.projectId,
                        current.spaceId,
                        openingId
                    )
                }
            )
        }

        is Screen.Opening -> {
            OpeningScreen(
                vm = vm,
                projectId = current.projectId,
                openingId = current.openingId,
                onBack = {
                    screen = Screen.Space(current.projectId, current.spaceId)
                },
                onCamera = {
                    screen = Screen.Camera(
                        current.projectId,
                        current.spaceId,
                        current.openingId
                    )
                },
                onEditEvidence = { evidenceId ->
                    screen = Screen.Evidence(
                        current.projectId,
                        current.spaceId,
                        current.openingId,
                        evidenceId
                    )
                },
                onDuplicated = { newOpeningId ->
                    screen = Screen.Opening(current.projectId, current.spaceId, newOpeningId)
                }
            )
        }

        is Screen.Camera -> {
            CameraCapture(
                onCaptured = { path ->
                    vm.addEvidence(
                        projectId = current.projectId,
                        openingId = current.openingId,
                        path = path
                    ) { evidenceId ->
                        screen = Screen.Evidence(
                            current.projectId,
                            current.spaceId,
                            current.openingId,
                            evidenceId
                        )
                    }
                },
                onCancel = {
                    screen = Screen.Opening(
                        current.projectId,
                        current.spaceId,
                        current.openingId
                    )
                }
            )
        }

        is Screen.Evidence -> {
            EvidenceEditorScreen(
                vm = vm,
                projectId = current.projectId,
                evidenceId = current.evidenceId,
                onBack = {
                    screen = Screen.Opening(
                        current.projectId,
                        current.spaceId,
                        current.openingId
                    )
                },
                onDeleted = {
                    screen = Screen.Opening(
                        current.projectId,
                        current.spaceId,
                        current.openingId
                    )
                },
                onShareImage = { file ->
                    val uri = FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.files",
                        file
                    )
                    val sendIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "image/jpeg"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(
                        Intent.createChooser(sendIntent, "Compartir fotografía técnica")
                    )
                }
            )
        }
    }
}
