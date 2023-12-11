package io.tolgee.service.export.exporters

import io.tolgee.dtos.IExportParams
import io.tolgee.dtos.request.export.ExportFormat
import io.tolgee.helpers.TextHelper
import io.tolgee.service.export.dataProvider.ExportTranslationView
import org.dom4j.DocumentHelper
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.*

class JavaPropertiesExporter(
    override val translations: List<ExportTranslationView>,
    override val exportParams: IExportParams,
) : FileExporter {

    override val fileExtension: String = ExportFormat.JAVA_PROPERTIES.extension

    val result = mutableMapOf<String, Properties>()

    override fun produceFiles(): Map<String, InputStream> {
        prepare()
        return result.asSequence().map { (fileName, properties) ->
            val outputStream = ByteArrayOutputStream()
            properties.store(outputStream, null)
            fileName to outputStream.toByteArray().inputStream()
        }.toMap()
    }

    private fun prepare() {
        translations.forEach { translation ->
            val path = TextHelper.splitOnNonEscapedDelimiter(translation.key.name, exportParams.structureDelimiter)
            val properties = getProperties(translation)
            addToProperties(properties, path, translation)
        }
    }

    private fun getProperties(translation: ExportTranslationView): Properties {
        val absolutePath = translation.getFilePath(translation.key.namespace)
        return result[absolutePath] ?: let {
            Properties().also { result[absolutePath] = it }
        }
    }

    private fun addToProperties(
        content: Properties,
        pathItems: List<String>,
        translation: ExportTranslationView
    ) {
        val pathItemsMutable = pathItems.toMutableList()
        val pathItem = pathItemsMutable.removeFirst()
        content[pathItem] = translation.text
    }
}
