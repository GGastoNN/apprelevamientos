package com.illu.relevametal.pdf

import android.content.Context
import android.graphics.*
import android.graphics.pdf.PdfDocument
import com.illu.relevametal.data.*
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

class ReportPdf(private val context: Context) {
    data class OpeningBundle(val opening: OpeningEntity, val evidence: List<EvidenceEntity>)
    data class SpaceBundle(val space: SpaceEntity, val openings: List<OpeningBundle>)

    fun generate(project: ProjectEntity, spaces: List<SpaceBundle>): File {
        val pdf=PdfDocument(); var pageNo=1
        fun newPage(): Pair<PdfDocument.Page, Canvas> {
            val info=PdfDocument.PageInfo.Builder(595,842,pageNo++).create()
            val p=pdf.startPage(info); return p to p.canvas
        }
        val title=Paint(Paint.ANTI_ALIAS_FLAG).apply{ textSize=22f; typeface=Typeface.DEFAULT_BOLD }
        val h2=Paint(Paint.ANTI_ALIAS_FLAG).apply{ textSize=15f; typeface=Typeface.DEFAULT_BOLD }
        val body=Paint(Paint.ANTI_ALIAS_FLAG).apply{ textSize=10f }
        var (page,canvas)=newPage(); var y=50f
        canvas.drawText("INFORME DE RELEVAMIENTO – CARPINTERÍA METÁLICA",36f,y,title); y+=35
        canvas.drawText("Obra: ${project.name}",36f,y,h2); y+=22
        canvas.drawText("Cliente: ${project.client}",36f,y,body); y+=16
        canvas.drawText("Dirección: ${project.address}",36f,y,body); y+=16
        canvas.drawText("Fecha: ${SimpleDateFormat("dd/MM/yyyy HH:mm",Locale.getDefault()).format(Date())}",36f,y,body); y+=26
        canvas.drawText("Observaciones: ${project.notes.take(120)}",36f,y,body); y+=30

        for (s in spaces) {
            if(y>760){ pdf.finishPage(page); val p=newPage(); page=p.first; canvas=p.second; y=50f }
            canvas.drawText("ESPACIO: ${s.space.name}",36f,y,h2); y+=22
            if(s.space.notes.isNotBlank()){ canvas.drawText(s.space.notes.take(100),36f,y,body); y+=18 }
            for (o in s.openings) {
                if(y>710){ pdf.finishPage(page); val p=newPage(); page=p.first; canvas=p.second; y=50f }
                val op=o.opening
                canvas.drawText("${op.code} · ${op.type} · ${op.status}",48f,y,h2); y+=18
                canvas.drawText("Ancho: ${op.widthMm ?: "—"} mm   Alto: ${op.heightMm ?: "—"} mm   Antepecho: ${op.sillMm ?: "—"} mm",48f,y,body); y+=16
                canvas.drawText("Diagonales: ${op.diagonal1Mm ?: "—"} / ${op.diagonal2Mm ?: "—"} mm",48f,y,body); y+=16
                if(op.notes.isNotBlank()){ canvas.drawText("Obs.: ${op.notes.take(95)}",48f,y,body); y+=18 }
                for(ev in o.evidence.take(2)){
                    val f=File(ev.filePath)
                    if(f.exists()){
                        val bmp=BitmapFactory.decodeFile(f.absolutePath)
                        if(bmp!=null){
                            val maxW=485f; val maxH=250f
                            val scale=minOf(maxW/bmp.width,maxH/bmp.height,1f)
                            val dst=RectF(48f,y,48f+bmp.width*scale,y+bmp.height*scale)
                            canvas.drawBitmap(bmp,null,dst,null); y=dst.bottom+12
                        }
                    }
                }
                y+=14
            }
        }
        pdf.finishPage(page)
        val dir=File(context.filesDir,"reports").apply{mkdirs()}
        val out=File(dir,"Relevamiento_${project.id}_${System.currentTimeMillis()}.pdf")
        FileOutputStream(out).use{pdf.writeTo(it)}; pdf.close(); return out
    }
}
