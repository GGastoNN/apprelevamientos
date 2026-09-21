@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.illu.relevametal

import android.Manifest
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.illu.relevametal.camera.CameraCapture
import com.illu.relevametal.data.OpeningEntity
import com.illu.relevametal.ui.AppViewModel

class MainActivity : ComponentActivity() {
    private val vm by viewModels<AppViewModel>()

    private val askCamera =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        askCamera.launch(Manifest.permission.CAMERA)

        setContent {
            MaterialTheme {
                App(vm)
            }
        }
    }
}

private sealed interface Screen {
    data object Projects : Screen
    data class Project(val id: Long) : Screen
    data class Space(val projectId: Long, val id: Long) : Screen
    data class Opening(val projectId: Long, val spaceId: Long, val id: Long) : Screen
    data class Camera(val projectId: Long, val spaceId: Long, val openingId: Long) : Screen
}

@Composable
private fun App(vm: AppViewModel) {
    val context = LocalContext.current
    var screen by remember { mutableStateOf<Screen>(Screen.Projects) }

    when (val s = screen) {
        Screen.Projects -> {
            ProjectsScreen(vm) { screen = Screen.Project(it) }
        }

        is Screen.Project -> {
            ProjectScreen(
                vm = vm,
                id = s.id,
                back = { screen = Screen.Projects },
                open = { spaceId -> screen = Screen.Space(s.id, spaceId) },
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
                        Intent.createChooser(sendIntent, "Compartir informe PDF")
                    )
                }
            )
        }

        is Screen.Space -> {
            SpaceScreen(
                vm = vm,
                id = s.id,
                back = { screen = Screen.Project(s.projectId) },
                open = { openingId ->
                    screen = Screen.Opening(s.projectId, s.id, openingId)
                }
            )
        }

        is Screen.Opening -> {
            OpeningScreen(
                vm = vm,
                id = s.id,
                back = { screen = Screen.Space(s.projectId, s.spaceId) },
                camera = { screen = Screen.Camera(s.projectId, s.spaceId, s.id) }
            )
        }

        is Screen.Camera -> {
            CameraCapture(
                onCaptured = {
                    vm.addEvidence(s.openingId, it)
                    screen = Screen.Opening(s.projectId, s.spaceId, s.openingId)
                },
                onCancel = {
                    screen = Screen.Opening(s.projectId, s.spaceId, s.openingId)
                }
            )
        }
    }
}

@Composable
private fun ProjectsScreen(vm: AppViewModel, onOpen: (Long) -> Unit) {
    val list by vm.projects.collectAsState()
    var dialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Grupo IDEA - Relevamientos") }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { dialog = true }) {
                Text("+")
            }
        }
    ) { pad ->
        LazyColumn(
            Modifier
                .padding(pad)
                .fillMaxSize()
                .padding(12.dp)
        ) {
            items(list) { p ->
                ElevatedCard(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 5.dp)
                        .clickable { onOpen(p.id) }
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(p.name, style = MaterialTheme.typography.titleMedium)
                        Text(p.client)
                        Text(p.address)
                    }
                }
            }
        }
    }

    if (dialog) {
        NewProjectDialog(
            cancel = { dialog = false },
            save = { n, c, a ->
                vm.addProject(n, c, a)
                dialog = false
            }
        )
    }
}

@Composable
private fun NewProjectDialog(
    cancel: () -> Unit,
    save: (String, String, String) -> Unit
) {
    var n by remember { mutableStateOf("") }
    var c by remember { mutableStateOf("") }
    var a by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = cancel,
        confirmButton = {
            Button(
                enabled = n.isNotBlank(),
                onClick = { save(n, c, a) }
            ) {
                Text("Guardar")
            }
        },
        dismissButton = {
            TextButton(onClick = cancel) {
                Text("Cancelar")
            }
        },
        title = { Text("Nueva obra") },
        text = {
            Column {
                OutlinedTextField(
                    value = n,
                    onValueChange = { n = it },
                    label = { Text("Nombre de obra") }
                )
                OutlinedTextField(
                    value = c,
                    onValueChange = { c = it },
                    label = { Text("Cliente") }
                )
                OutlinedTextField(
                    value = a,
                    onValueChange = { a = it },
                    label = { Text("Dirección") }
                )
            }
        }
    )
}

@Composable
private fun ProjectScreen(
    vm: AppViewModel,
    id: Long,
    back: () -> Unit,
    open: (Long) -> Unit,
    onSharePdf: (java.io.File) -> Unit
) {
    val spaces by vm.spaces(id).collectAsState(initial = emptyList())
    var name by remember { mutableStateOf("") }
    var exporting by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Espacios / sectores") },
                navigationIcon = {
                    TextButton(onClick = back) {
                        Text("←")
                    }
                },
                actions = {
                    TextButton(
                        enabled = !exporting,
                        onClick = {
                            exporting = true
                            vm.exportProject(id) { file ->
                                exporting = false
                                onSharePdf(file)
                            }
                        }
                    ) {
                        Text(if (exporting) "Generando…" else "PDF")
                    }
                }
            )
        }
    ) { p ->
        Column(
            Modifier
                .padding(p)
                .padding(12.dp)
        ) {
            Row {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    modifier = Modifier.weight(1f),
                    label = { Text("Nuevo espacio") }
                )
                Button(
                    onClick = {
                        if (name.isNotBlank()) {
                            vm.addSpace(id, name)
                            name = ""
                        }
                    },
                    modifier = Modifier.padding(start = 8.dp)
                ) {
                    Text("Agregar")
                }
            }

            LazyColumn {
                items(spaces) { s ->
                    ListItem(
                        headlineContent = { Text(s.name) },
                        modifier = Modifier.clickable { open(s.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun SpaceScreen(
    vm: AppViewModel,
    id: Long,
    back: () -> Unit,
    open: (Long) -> Unit
) {
    val openings by vm.openings(id).collectAsState(initial = emptyList())
    var code by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Vanos") },
                navigationIcon = {
                    TextButton(onClick = back) {
                        Text("←")
                    }
                }
            )
        }
    ) { p ->
        Column(
            Modifier
                .padding(p)
                .padding(12.dp)
        ) {
            Row {
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it },
                    modifier = Modifier.weight(1f),
                    label = { Text("Código (V01)") }
                )
                Button(
                    onClick = {
                        if (code.isNotBlank()) {
                            vm.addOpening(id, code)
                            code = ""
                        }
                    },
                    modifier = Modifier.padding(start = 8.dp)
                ) {
                    Text("Agregar")
                }
            }

            LazyColumn {
                items(openings) { o ->
                    ListItem(
                        headlineContent = { Text(o.code) },
                        supportingContent = {
                            Text(
                                "${o.widthMm ?: "—"} × ${o.heightMm ?: "—"} mm · ${o.status}"
                            )
                        },
                        modifier = Modifier.clickable { open(o.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun OpeningScreen(
    vm: AppViewModel,
    id: Long,
    back: () -> Unit,
    camera: () -> Unit
) {
    var opening by remember(id) {
        mutableStateOf<OpeningEntity?>(null)
    }

    LaunchedEffect(id) {
        opening = vm.db.openings().get(id)
    }

    val evidence by vm.evidence(id).collectAsState(initial = emptyList())
    val o = opening ?: return

    var w by remember(o) { mutableStateOf(o.widthMm?.toString() ?: "") }
    var h by remember(o) { mutableStateOf(o.heightMm?.toString() ?: "") }
    var sill by remember(o) { mutableStateOf(o.sillMm?.toString() ?: "") }
    var notes by remember(o) { mutableStateOf(o.notes) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(o.code) },
                navigationIcon = {
                    TextButton(onClick = back) {
                        Text("←")
                    }
                }
            )
        }
    ) { p ->
        LazyColumn(
            Modifier
                .padding(p)
                .padding(12.dp)
        ) {
            item {
                Text(
                    "Cotas del vano",
                    style = MaterialTheme.typography.titleLarge
                )

                Row {
                    OutlinedTextField(
                        value = w,
                        onValueChange = { w = it },
                        modifier = Modifier.weight(1f),
                        label = { Text("Ancho mm") }
                    )
                    Spacer(Modifier.width(8.dp))
                    OutlinedTextField(
                        value = h,
                        onValueChange = { h = it },
                        modifier = Modifier.weight(1f),
                        label = { Text("Alto mm") }
                    )
                }

                OutlinedTextField(
                    value = sill,
                    onValueChange = { sill = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Antepecho mm") }
                )

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Comentarios / incidencias") },
                    minLines = 3
                )

                Row {
                    Button(
                        onClick = {
                            val updated = o.copy(
                                widthMm = w.toIntOrNull(),
                                heightMm = h.toIntOrNull(),
                                sillMm = sill.toIntOrNull(),
                                notes = notes
                            )
                            vm.saveOpening(updated)
                            opening = updated
                        }
                    ) {
                        Text("Guardar")
                    }

                    Spacer(Modifier.width(8.dp))

                    Button(onClick = camera) {
                        Text("Tomar foto")
                    }
                }

                Text(
                    "Evidencias: ${evidence.size}",
                    Modifier.padding(top = 18.dp),
                    style = MaterialTheme.typography.titleMedium
                )

                Text(
                    "La siguiente etapa incorpora anotación sobre la imagen, " +
                        "detección asistida del contorno y exportación PDF desde la obra."
                )
            }
        }
    }
}
