package com.epam.dsm.util

import java.util.*
import java.util.stream.IntStream


fun BitSet.stringRepresentation(): String {
    val size = length() - 1 //bitset issue.
    return IntStream
        .range(0, size)
        .mapToObj { i: Int -> if (get(i)) '1' else '0' }
        .collect(
            { StringBuilder(size) },
            { buffer: StringBuilder, characterToAdd: Char -> buffer.append(characterToAdd) },
            { obj: StringBuilder, s: StringBuilder -> obj.append(s) })
        .toString()
}

fun String.toBitSet(): BitSet {
    val bitSet = BitSet(length + 1)
    bitSet.set(length)//magic
    forEachIndexed { inx, ch ->
        if (ch == '1') {
            bitSet.set(inx)
        }
    }
    return bitSet
}