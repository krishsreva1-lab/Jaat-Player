

package com.krish.jaatplayer.models

import com.music.innertube.models.YTItem
import com.krish.jaatplayer.db.entities.LocalItem

data class SimilarRecommendation(
    val title: LocalItem,
    val items: List<YTItem>,
)
