package com.girlspace.app.ui.feed

import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.girlspace.app.R
import com.google.android.gms.ads.nativead.MediaView
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdView

@Composable
fun NativeAdCard(
    nativeAd: NativeAd,
    modifier: Modifier = Modifier
) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            val view = LayoutInflater.from(context)
                .inflate(R.layout.native_feed_ad, null, false) as NativeAdView
            view
        },
        update = { adView ->
            // Views from XML
            val headline = adView.findViewById<TextView>(R.id.ad_headline)
            val body = adView.findViewById<TextView>(R.id.ad_body)
            val cta = adView.findViewById<Button>(R.id.ad_cta)
            val media = adView.findViewById<MediaView>(R.id.ad_media)

            // Attach views to NativeAdView
            adView.headlineView = headline
            adView.bodyView = body
            adView.callToActionView = cta
            adView.mediaView = media

            // Bind assets safely
            headline.text = nativeAd.headline ?: ""
            headline.visibility = if (nativeAd.headline.isNullOrBlank()) View.GONE else View.VISIBLE

            body.text = nativeAd.body ?: ""
            body.visibility = if (nativeAd.body.isNullOrBlank()) View.GONE else View.VISIBLE

            cta.text = nativeAd.callToAction ?: ""
            cta.visibility = if (nativeAd.callToAction.isNullOrBlank()) View.GONE else View.VISIBLE

            // Media content (image/video). If no media, hide media view.
            val hasMedia = nativeAd.mediaContent != null
            media.visibility = if (hasMedia) View.VISIBLE else View.GONE
            if (hasMedia) {
                media.mediaContent = nativeAd.mediaContent
            }

            // Final step (this is what actually “renders” the ad)
            adView.setNativeAd(nativeAd)
        }
    )
}
