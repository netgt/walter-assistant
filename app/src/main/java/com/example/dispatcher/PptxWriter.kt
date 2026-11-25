package com.example.dispatcher

import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Минимальный записыватель PPTX без java.awt (безопасен на Android):
 * создаёт валидный OOXML-файл, где каждый слайд — текстовый блок из абзацев,
 * подготовленных моделью. Чистая функция — тестируется в JVM.
 */
object PptxWriter {

    private const val NS_A = "xmlns:a=\"http://schemas.openxmlformats.org/drawingml/2006/main\""
    private const val NS_R = "xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\""
    private const val NS_P = "xmlns:p=\"http://schemas.openxmlformats.org/presentationml/2006/main\""
    private const val R_OFFICE = "http://schemas.openxmlformats.org/officeDocument/2006/relationships/"

    /** Разбор текста модели в слайды: строка «## Слайд N» начинает новый слайд. */
    fun parseSlides(content: String): List<List<String>> {
        val slides = mutableListOf<MutableList<String>>()
        var cur: MutableList<String>? = null
        for (raw in content.split("\n")) {
            val line = raw.trimEnd('\r').trim()
            if (line.startsWith("##")) {
                cur = mutableListOf()
                val title = line.removePrefix("##").trim()
                if (title.isNotBlank()) cur.add(title)
                slides.add(cur)
            } else if (line.isNotBlank()) {
                if (cur == null) {
                    cur = mutableListOf()
                    slides.add(cur)
                }
                cur.add(line)
            }
        }
        if (slides.isEmpty()) slides.add(mutableListOf())
        return slides
    }

    fun write(path: String, slides: List<List<String>>): String {
        FileOutputStream(path).use { fos ->
            ZipOutputStream(fos).use { zip ->
                // [Content_Types].xml
                val ct = StringBuilder(
                    "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n" +
                        "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">" +
                        "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>" +
                        "<Default Extension=\"xml\" ContentType=\"application/xml\"/>"
                )
                ct.append("<Override PartName=\"/ppt/presentation.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.presentationml.presentation.main+xml\"/>")
                ct.append("<Override PartName=\"/ppt/slideMasters/slideMaster1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.presentationml.slideMaster+xml\"/>")
                ct.append("<Override PartName=\"/ppt/slideLayouts/slideLayout1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.presentationml.slideLayout+xml\"/>")
                slides.indices.forEach { i ->
                    ct.append("<Override PartName=\"/ppt/slides/slide${i + 1}.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.presentationml.slide+xml\"/>")
                }
                ct.append("</Types>")
                zip.put("[Content_Types].xml", ct.toString())

                zip.put(
                    "_rels/.rels",
                    rels(listOf(Triple("rId1", R_OFFICE + "officeDocument", "ppt/presentation.xml")))
                )

                // ppt/presentation.xml + его связи
                val sldIds = StringBuilder()
                val presRels = mutableListOf(Triple("rId1", R_OFFICE + "slideMaster", "slideMasters/slideMaster1.xml"))
                slides.indices.forEach { i ->
                    val n = i + 1
                    sldIds.append("<p:sldId id=\"${256 + i}\" r:id=\"rId${n + 1}\"/>")
                    presRels += Triple("rId${n + 1}", R_OFFICE + "slide", "slides/slide$n.xml")
                }
                zip.put(
                    "ppt/presentation.xml",
                    "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n" +
                        "<p:presentation $NS_A $NS_R $NS_P>" +
                        "<p:sldMasterIdLst><p:sldMasterId id=\"2147483648\" r:id=\"rId1\"/></p:sldMasterIdLst>" +
                        "<p:sldIdLst>$sldIds</p:sldIdLst>" +
                        "<p:sldSz cx=\"9144000\" cy=\"6858000\"/></p:presentation>"
                )
                zip.put("ppt/_rels/presentation.xml.rels", rels(presRels))

                // мастер и макет
                zip.put("ppt/slideMasters/slideMaster1.xml", masterXml())
                zip.put(
                    "ppt/slideMasters/_rels/slideMaster1.xml.rels",
                    rels(listOf(Triple("rId1", R_OFFICE + "slideLayout", "../slideLayouts/slideLayout1.xml")))
                )
                zip.put("ppt/slideLayouts/slideLayout1.xml", layoutXml())
                zip.put(
                    "ppt/slideLayouts/_rels/slideLayout1.xml.rels",
                    rels(listOf(Triple("rId1", R_OFFICE + "slideMaster", "../slideMasters/slideMaster1.xml")))
                )

                slides.forEachIndexed { i, lines ->
                    zip.put("ppt/slides/slide${i + 1}.xml", slideXml(lines))
                }
            }
        }
        return "Файл записан (pptx)"
    }

    private fun masterXml(): String =
        "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n" +
            "<p:sldMaster $NS_A $NS_R $NS_P>" +
            "<p:cSld><p:bg><p:bgPr><a:solidFill><a:srgbClr val=\"FFFFFF\"/></a:solidFill><a:effectLst/></p:bgPr></p:bg>" +
            "<p:spTree><p:nvGrpSpPr><p:cNvPr id=\"1\" name=\"\"/><p:cNvGrpSpPr/><p:nvPr/></p:nvGrpSpPr><p:grpSpPr/></p:spTree></p:cSld>" +
            "<p:clrMap bg1=\"lt1\" tx1=\"dk1\" bg2=\"lt2\" tx2=\"dk2\" accent1=\"accent1\" accent2=\"accent2\" accent3=\"accent3\" accent4=\"accent4\" accent5=\"accent5\" accent6=\"accent6\" hlink=\"hlink\" folHlink=\"folHlink\"/>" +
            "<p:sldLayoutIdLst><p:sldLayoutId id=\"2147483649\" r:id=\"rId1\"/></p:sldLayoutIdLst>" +
            "</p:sldMaster>"

    private fun layoutXml(): String =
        "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n" +
            "<p:sldLayout $NS_A $NS_R $NS_P>" +
            "<p:cSld><p:spTree><p:nvGrpSpPr><p:cNvPr id=\"1\" name=\"\"/><p:cNvGrpSpPr/><p:nvPr/></p:nvGrpSpPr><p:grpSpPr/></p:spTree></p:cSld>" +
            "<p:clrMapOvr><a:overrideClrMapping bg1=\"lt1\" tx1=\"dk1\" bg2=\"lt2\" tx2=\"dk2\" accent1=\"accent1\" accent2=\"accent2\" accent3=\"accent3\" accent4=\"accent4\" accent5=\"accent5\" accent6=\"accent6\" hlink=\"hlink\" folHlink=\"folHlink\"/></p:clrMapOvr>" +
            "</p:sldLayout>"

    private fun slideXml(lines: List<String>): String {
        val body = if (lines.isEmpty()) "" else {
            val paras = lines.joinToString("") { ln ->
                "<a:p><a:r><a:rPr lang=\"ru-RU\" sz=\"1800\"/><a:t>${xml(ln)}</a:t></a:r></a:p>"
            }
            "<p:sp><p:nvSpPr><p:cNvPr id=\"2\" name=\"TextBox\"/><p:cNvSpPr txBox=\"1\"/><p:nvPr/></p:nvSpPr>" +
                "<p:spPr><a:xfrm><a:off x=\"457200\" y=\"1371600\"/><a:ext cx=\"8229600\" cy=\"4114800\"/></a:xfrm>" +
                "<a:prstGeom prst=\"rect\"><a:avLst/></a:prstGeom></p:spPr>" +
                "<p:txBody><a:bodyPr wrap=\"square\"/><a:lstStyle/>$paras</p:txBody></p:sp>"
        }
        return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n" +
            "<p:sld $NS_A $NS_R $NS_P><p:cSld><p:spTree>" +
            "<p:nvGrpSpPr><p:cNvPr id=\"1\" name=\"\"/><p:cNvGrpSpPr/><p:nvPr/></p:nvGrpSpPr><p:grpSpPr/>" +
            body + "</p:spTree></p:cSld></p:sld>"
    }

    private fun rels(items: List<Triple<String, String, String>>): String {
        val sb = StringBuilder(
            "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n" +
                "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
        )
        items.forEach { (id, type, target) ->
            sb.append("<Relationship Id=\"$id\" Type=\"$type\" Target=\"$target\"/>")
        }
        sb.append("</Relationships>")
        return sb.toString()
    }

    private fun ZipOutputStream.put(name: String, content: String) {
        putNextEntry(ZipEntry(name))
        write(content.toByteArray(Charsets.UTF_8))
        closeEntry()
    }

    private fun xml(s: String): String = s
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")
}
