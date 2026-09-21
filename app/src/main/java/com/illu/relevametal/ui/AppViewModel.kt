package com.illu.relevametal.ui

import android.app.Application
import android.graphics.BitmapFactory
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.illu.relevametal.data.*
import com.illu.relevametal.detection.OpeningDetector
import com.illu.relevametal.pdf.ReportPdf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class AppViewModel(app:Application):AndroidViewModel(app){
    val db=AppDatabase.get(app)
    private val detector=OpeningDetector()
    val projects=db.projects().observeProjects().stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000), emptyList())

    fun addProject(name:String, client:String, address:String)=viewModelScope.launch{
        val id=db.projects().insert(ProjectEntity(name=name,client=client,address=address))
        db.events().insert(EventEntity(projectId=id,kind="CREATED",title="Obra creada"))
    }
    fun spaces(projectId:Long)=db.spaces().observe(projectId)
    fun openings(spaceId:Long)=db.openings().observe(spaceId)
    fun evidence(openingId:Long)=db.evidence().observe(openingId)

    fun addSpace(projectId:Long,name:String)=viewModelScope.launch{
        db.spaces().insert(SpaceEntity(projectId=projectId,name=name))
        db.events().insert(EventEntity(projectId=projectId,kind="SPACE",title="Espacio agregado",detail=name))
    }
    fun addOpening(spaceId:Long,code:String)=viewModelScope.launch{ db.openings().insert(OpeningEntity(spaceId=spaceId,code=code)) }
    fun saveOpening(o:OpeningEntity)=viewModelScope.launch{ db.openings().update(o.copy(updatedAt=System.currentTimeMillis())) }

    fun addEvidence(openingId:Long,path:String)=viewModelScope.launch(Dispatchers.IO){
        val bitmap=BitmapFactory.decodeFile(path)
        val detected=bitmap?.let { detector.detect(it) }.orEmpty()
        val json=detected.joinToString(prefix="[",postfix="]") { c ->
            "{\"l\":${c.rect.left},\"t\":${c.rect.top},\"r\":${c.rect.right},\"b\":${c.rect.bottom},\"confidence\":${c.confidence}}"
        }
        db.evidence().insert(EvidenceEntity(openingId=openingId,filePath=path,detectedJson=json))
    }

    fun exportProject(projectId:Long,onReady:(File)->Unit)=viewModelScope.launch{
        val file=withContext(Dispatchers.IO){
            val project=db.projects().project(projectId) ?: error("Obra inexistente")
            val spaces=db.spaces().list(projectId).map { space ->
                val openings=db.openings().list(space.id).map { opening ->
                    ReportPdf.OpeningBundle(opening,db.evidence().list(opening.id))
                }
                ReportPdf.SpaceBundle(space,openings)
            }
            ReportPdf(getApplication()).generate(project,spaces)
        }
        onReady(file)
    }
}
