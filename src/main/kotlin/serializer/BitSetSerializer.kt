package serializer

import com.epam.dsm.util.stringRepresentation
import com.epam.dsm.util.toBitSet
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.util.*


//search select bit_or(Cast(JSON_BODY->>'bitset' as BIT VARYING(10000000))) FROM bittest.bitset_class
object BitSetSerializer : KSerializer<BitSet> {

    override fun serialize(encoder: Encoder, value: BitSet) {
        encoder.encodeSerializableValue(String.serializer(), value.stringRepresentation())
    }

    override fun deserialize(decoder: Decoder): BitSet {
        val decodeSerializableValue = decoder.decodeSerializableValue(String.serializer())
        return decodeSerializableValue.toBitSet()
    }

    override val descriptor: SerialDescriptor
        get() = buildClassSerialDescriptor("BitSet")
}