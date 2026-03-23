package com.musa.t9keyboard

class BKTree {
    private class Node(val word: String, val frequency: Int) {
        val children = mutableMapOf<Int, Node>()
    }

    private var root: Node? = null

    fun insert(word: String, frequency: Int) {
        val newNode = Node(word.lowercase(), frequency)
        if (root == null) {
            root = newNode
            return
        }

        var curr = root
        while (curr != null) {
            val dist = editDistance(curr.word, newNode.word)
            if (dist == 0) return // Word already exists

            val next = curr.children[dist]
            if (next == null) {
                curr.children[dist] = newNode
                break
            }
            curr = next
        }
    }

    fun search(query: String, maxDistance: Int): List<Pair<String, Int>> {
        val result = mutableListOf<Pair<String, Int>>()
        val lowercaseQuery = query.lowercase()
        val queue = mutableListOf<Node>()
        root?.let { queue.add(it) }

        var head = 0
        while (head < queue.size) {
            val curr = queue[head++]
            val dist = editDistance(curr.word, lowercaseQuery)

            if (dist <= maxDistance) {
                result.add(curr.word to curr.frequency)
            }

            val minDist = dist - maxDistance
            val maxDist = dist + maxDistance

            for ((d, child) in curr.children) {
                if (d in minDist..maxDist) {
                    queue.add(child)
                }
            }
        }
        return result
    }

    private fun editDistance(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length

        val n = a.length
        val m = b.length
        var prev = IntArray(m + 1) { it }
        var curr = IntArray(m + 1)

        for (i in 1..n) {
            curr[0] = i
            for (j in 1..m) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                curr[j] = minOf(
                    curr[j - 1] + 1,
                    minOf(prev[j] + 1, prev[j - 1] + cost)
                )
            }
            val temp = prev
            prev = curr
            curr = temp
        }
        return prev[m]
    }
}
