package com.girlspace.app.ads

import android.content.Context
import com.google.android.gms.ads.*
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdOptions

class NativeAdLoader(
    private val context: Context,
    private val adUnitId: String
) {

    fun load(
        onLoaded: (NativeAd) -> Unit,
        onFailed: () -> Unit
    ) {
        val loader = AdLoader.Builder(context, adUnitId)
            .forNativeAd { ad ->
                onLoaded(ad)
            }
            .withAdListener(object : AdListener() {
                override fun onAdFailedToLoad(error: LoadAdError) {
                    onFailed()
                }
            })
            .withNativeAdOptions(
                NativeAdOptions.Builder()
                    .setAdChoicesPlacement(NativeAdOptions.ADCHOICES_TOP_RIGHT)
                    .build()
            )
            .build()

        loader.loadAd(AdRequest.Builder().build())
    }
}
