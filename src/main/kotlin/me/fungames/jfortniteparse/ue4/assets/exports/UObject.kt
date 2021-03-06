package me.fungames.jfortniteparse.ue4.assets.exports

import com.github.salomonbrys.kotson.jsonObject
import com.github.salomonbrys.kotson.set
import com.google.gson.Gson
import com.google.gson.JsonObject
import me.fungames.jfortniteparse.exceptions.ParserException
import me.fungames.jfortniteparse.ue4.assets.JsonSerializer.toJson
import me.fungames.jfortniteparse.ue4.assets.Package
import me.fungames.jfortniteparse.ue4.assets.objects.FPropertyTag
import me.fungames.jfortniteparse.ue4.assets.reader.FAssetArchive
import me.fungames.jfortniteparse.ue4.assets.util.mapToClass
import me.fungames.jfortniteparse.ue4.assets.writer.FAssetArchiveWriter
import me.fungames.jfortniteparse.ue4.locres.Locres
import me.fungames.jfortniteparse.ue4.objects.core.misc.FGuid
import me.fungames.jfortniteparse.ue4.objects.uobject.FName
import me.fungames.jfortniteparse.ue4.objects.uobject.FObjectExport

@ExperimentalUnsignedTypes
open class UObject : UExport {
    lateinit var properties: MutableList<FPropertyTag>
    var objectGuid: FGuid? = null
    var readGuid = false

    @JvmOverloads
    constructor(exportObject: FObjectExport, readGuid: Boolean = true) : super(exportObject) {
        this.readGuid = readGuid
    }

    /** Arbitrary UObject construction */
    constructor() : this(mutableListOf(), null, "") {
        exportType = javaClass.simpleName
        name = javaClass.simpleName
    }

    /** For use in UDataTable and UCurveTable */
    constructor(properties: MutableList<FPropertyTag>, objectGuid: FGuid?, exportType: String) : super(exportType) {
        this.properties = properties
        this.objectGuid = objectGuid
    }

    inline fun <reified T> set(name: String, value: T) {
        if (getOrNull<T>(name) != null)
            properties.first { it.name.text == name }.setTagTypeValue(value)
    }

    inline fun <reified T> getOrDefault(name: String, default: T): T {
        val value: T? = getOrNull(name)
        return value ?: default
    }

    fun <T> getOrNull(name: String, clazz: Class<T>): T? = properties.firstOrNull { it.name.text == name }?.getTagTypeValue(clazz)

    inline fun <reified T> getOrNull(name: String) = getOrNull(name, T::class.java)

    inline fun <reified T> get(name: String): T = getOrNull(name) ?: throw KotlinNullPointerException("$name must be not-null")

    override fun deserialize(Ar: FAssetArchive, validPos: Int) {
        super.init(Ar)
        properties = deserializeProperties(Ar)
        if (readGuid && Ar.readBoolean() && Ar.pos() + 16 <= Ar.size())
            objectGuid = FGuid(Ar)
        super.complete(Ar)
        mapToClass(properties, javaClass, this)
    }

    override fun serialize(Ar: FAssetArchiveWriter) {
        super.initWrite(Ar)
        serializeProperties(Ar, properties)
        if (readGuid) {
            Ar.writeBoolean(objectGuid != null)
            if (objectGuid != null) objectGuid?.serialize(Ar)
        }
        super.completeWrite(Ar)
    }

    fun toJson(context: Gson = Package.gson, locres: Locres? = null) : JsonObject {
        val ob = jsonObject("exportType" to exportType)
        properties.forEach { pTag ->
            val tagValue = pTag.tag ?: return@forEach
            ob[pTag.name.text] = tagValue.toJson(context, locres)
        }
        return ob
    }

    companion object {
        fun deserializeProperties(Ar: FAssetArchive): MutableList<FPropertyTag> {
            val properties = mutableListOf<FPropertyTag>()
            while (true) {
                val tag = FPropertyTag(Ar, true)
                if (tag.name.text == "None")
                    break
                properties.add(tag)
            }
            return properties
        }

        fun serializeProperties(Ar: FAssetArchiveWriter, properties: List<FPropertyTag>) {
            properties.forEach { it.serialize(Ar, true) }
            Ar.writeFName(FName.getByNameMap("None", Ar.nameMap) ?: throw ParserException("NameMap must contain \"None\""))
        }
    }
}